import copy
import unittest
import json
from pathlib import Path
from beginner_companion.domain.catalog import load_catalog, evaluate_goals
from beginner_companion.domain.recommendations import recommend
from test_companion_goals import demo


class PreferenceMatchingTests(unittest.TestCase):
    def setUp(self):
        self.catalog = load_catalog()

    def result(self, name='partial', interests=(), commitment='any', account=None):
        account = demo(name)['account'] if account is None else account
        return recommend(self.catalog, evaluate_goals(self.catalog, account),
                         dict(interests=list(interests), commitment=commitment), account)

    def test_meaningful_matches_and_explanations(self):
        cases = [('partial', ['combat'], 'long', 'wear_rune_platebody'),
                 ('partial', ['exploration'], 'few_sessions', 'ardougne_cloak_1'),
                 ('fresh', ['quests', 'utility'], 'short', 'prince_ali_rescue')]
        for account, interests, commitment, goal in cases:
            with self.subTest(goal=goal):
                result = self.result(account, interests, commitment)
                self.assertEqual(result['recommended_goal_id'], goal)
                self.assertEqual(result['goals'][0]['goal']['id'], goal)
                reasons = ' '.join(result['goals'][0]['recommendation']['reasons'])
                self.assertIn('Matches your interest', reasons)
                self.assertIn('fits your preference', reasons)
                self.assertEqual(len(result['goals']), 33)

    def test_no_answers_no_fake_personalisation_and_stable_order(self):
        result = self.result()
        self.assertIsNone(result['recommended_goal_id'])
        self.assertIn('mix to explore', result['recommendation_summary'])
        self.assertEqual([r['goal']['id'] for r in result['goals']],
                         [g['id'] for g in self.catalog['goals'] if next(r for r in result['goals'] if r['goal']['id'] == g['id'])['status'] != 'completed']
                         + [g['id'] for g in self.catalog['goals'] if next(r for r in result['goals'] if r['goal']['id'] == g['id'])['status'] == 'completed'])
        self.assertFalse(any(r['recommendation']['recommended'] for r in result['goals']))

    def test_completion_and_membership_take_priority(self):
        result = self.result('fresh', ['exploration'], 'few_sessions')
        cloak = next(r for r in result['goals'] if r['goal']['id'] == 'ardougne_cloak_1')
        self.assertEqual(cloak['recommendation']['availability'], 'members_required')
        self.assertNotEqual(result['recommended_goal_id'], 'ardougne_cloak_1')
        self.assertFalse(cloak['recommendation']['recommended'])
        self.assertIn('membership', ' '.join(cloak['recommendation']['considerations']))
        completed = self.result('complete', ['combat'], 'long')
        self.assertIsNone(completed['recommended_goal_id'])
        self.assertIn('All catalogue goals are completed', completed['recommendation_summary'])
        # A highly matched completed short quest cannot outrank unfinished goals.
        partial = self.result('partial', ['quests', 'utility'], 'short')
        self.assertNotEqual(partial['recommended_goal_id'], 'prince_ali_rescue')
        self.assertEqual(next(r for r in partial['goals'] if r['goal']['id'] == 'prince_ali_rescue')['recommendation']['availability'], 'completed')

    def test_unknowns_are_qualified_and_not_invented(self):
        result = self.result('unknown', ['combat'], 'long')
        self.assertEqual(result['recommended_goal_id'], 'wear_rune_platebody')
        self.assertIn('Completion is unknown', ' '.join(result['goals'][0]['recommendation']['considerations']))
        cloak = next(r for r in result['goals'] if r['goal']['id'] == 'ardougne_cloak_1')
        self.assertEqual(cloak['recommendation']['availability'], 'membership_unknown')
        self.assertEqual(cloak['status'], 'unknown')

    def test_mismatch_no_match_ties_and_no_mutation(self):
        account = demo('partial')['account']
        evaluated = evaluate_goals(self.catalog, account)
        before = copy.deepcopy(evaluated)
        first = recommend(self.catalog, evaluated, dict(interests=['combat'], commitment='short'), account)
        rune = next(row for row in first['goals'] if row['goal']['id'] == 'wear_rune_platebody')
        self.assertIn('larger commitment', ' '.join(rune['recommendation']['considerations']))
        self.assertEqual(evaluated, before)
        self.assertEqual(first, recommend(self.catalog, evaluated, dict(interests=['combat'], commitment='short'), account))
        # Available goals already done; only a membership-blocked match remains.
        account = demo('complete')['account']
        account['milestones']['ardougne_cloak_1_claimed'] = False
        account['quest_progress']['plague_city'] = 'not_started'
        account['members'] = False
        result = self.result(interests=['exploration'], account=account)
        self.assertIsNone(result['recommended_goal_id'])
        self.assertIn('No available unfinished goal', result['recommendation_summary'])
        self.assertEqual(self.result(interests=['combat', 'utility']), self.result(interests=['utility', 'combat']))


try:
    from fastapi.testclient import TestClient
    from beginner_companion.api.app import app
except ImportError:
    TestClient = None


@unittest.skipIf(TestClient is None, 'Install optional API dependencies')
class PreferenceApiTests(unittest.TestCase):
    def test_shared_preference_fixtures(self):
        fixtures = Path(__file__).resolve().parents[1] / 'contracts/fixtures'
        with TestClient(app) as client:
            for name in ('combat', 'exploration', 'short'):
                request = json.loads((fixtures / f'preferences-{name}.request.json').read_text(encoding='utf-8'))
                expected = json.loads((fixtures / f'preferences-{name}.response.json').read_text(encoding='utf-8'))
                response = client.post('/v1/goals/evaluate', json=request)
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json(), expected)

    def test_validation_and_defaults(self):
        with TestClient(app) as client:
            payload = demo('partial')
            old = client.post('/v1/goals/evaluate', json=payload).json()
            payload['preferences'] = {}
            self.assertEqual(client.post('/v1/goals/evaluate', json=payload).json(), old)
            for prefs in (None, {'interests': ['money']}, {'interests': ['combat', 'combat']},
                          {'interests': 'combat'}, {'commitment': True}, {'commitment': 'fast'},
                          {'extra': 'value'}):
                payload['preferences'] = prefs
                self.assertEqual(client.post('/v1/goals/evaluate', json=payload).status_code, 422)
