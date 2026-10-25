import copy
import unittest
from fastapi.testclient import TestClient
from beginner_companion.api.app import app
from beginner_companion.domain.catalog import load_catalog, evaluate_goals
from beginner_companion.domain.recommendations import recommend
from test_companion_goals import demo


class RecommendationFeedbackTests(unittest.TestCase):
    NOW = 1800000000

    def result(self, feedback=None, account=None, interests=('combat',), commitment='long'):
        account = demo('partial')['account'] if account is None else account
        catalog = load_catalog()
        return recommend(catalog, evaluate_goals(catalog, account),
                         {'interests': list(interests), 'commitment': commitment}, account,
                         feedback=feedback, now=self.NOW)

    def feedback(self, kind, age=0, goal='wear_rune_platebody'):
        return {goal: {'kind': kind, 'recorded_at': self.NOW - age}}

    def test_all_three_choices_exclude_the_goal_without_removing_it(self):
        for kind in ['not_interested', 'maybe_later', 'too_much_work']:
            with self.subTest(kind=kind):
                result = self.result(self.feedback(kind))
                self.assertNotEqual(result['recommended_goal_id'], 'wear_rune_platebody')
                goal = next(g for g in result['goals'] if g['goal']['id'] == 'wear_rune_platebody')
                self.assertFalse(goal['recommendation']['recommended'])
                self.assertEqual(goal['recommendation']['feedback_status'], kind)
                self.assertTrue(goal['recommendation']['feedback_reason'])
                self.assertEqual(len(result['goals']), 15)

    def test_expiry_boundaries_future_dates_and_unknown_ids(self):
        baseline = self.result()
        for kind, duration in [('maybe_later', 86400), ('too_much_work', 7 * 86400)]:
            self.assertNotEqual(self.result(self.feedback(kind, duration - 1))['recommended_goal_id'], 'wear_rune_platebody')
            self.assertEqual(self.result(self.feedback(kind, duration)), baseline)
        self.assertNotEqual(self.result(self.feedback('not_interested', 365 * 86400)), baseline)
        self.assertEqual(self.result(self.feedback('not_interested', -1)), baseline)
        self.assertEqual(self.result(self.feedback('not_interested', goal='unknown_goal')), baseline)

    def test_too_much_work_only_boosts_confirmed_short_interest_matches(self):
        account = demo('unclaimed')['account']
        result = self.result(self.feedback('too_much_work', goal='attack_40'), account, commitment='any')
        rune = next(g for g in result['goals'] if g['goal']['id'] == 'wear_rune_platebody')
        cloak = next(g for g in result['goals'] if g['goal']['id'] == 'ardougne_cloak_1')
        self.assertTrue(any('less work' in r for r in rune['recommendation']['reasons']))
        self.assertFalse(any('less work' in r for r in cloak['recommendation']['reasons']))
        account['milestones'].pop('rune_platebody_equipped')
        result = self.result(self.feedback('too_much_work', goal='attack_40'), account, commitment='any')
        rune = next(g for g in result['goals'] if g['goal']['id'] == 'wear_rune_platebody')
        self.assertFalse(any('less work' in r for r in rune['recommendation']['reasons']))

    def test_all_suppressed_and_reset_no_mutation(self):
        catalog = load_catalog()
        feedback = {g['id']: {'kind': 'not_interested', 'recorded_at': self.NOW} for g in catalog['goals']}
        original = copy.deepcopy(feedback)
        result = self.result(feedback, demo('fresh')['account'])
        self.assertIsNone(result['recommended_goal_id'])
        self.assertIn('Reset feedback', result['recommendation_summary'])
        self.assertEqual(feedback, original)
        self.assertEqual(self.result({}), self.result())
        self.assertEqual(self.result(feedback, demo('complete')['account'])['recommended_goal_id'], None)

    def test_feedback_api_validation_and_optional_default(self):
        payload = demo('partial')
        with TestClient(app) as client:
            baseline = client.post('/v1/goals/evaluate', json=payload).json()
            payload['recommendation_feedback'] = {}
            self.assertEqual(client.post('/v1/goals/evaluate', json=payload).json(), baseline)
            for bad in [{'x': {'kind':'invented', 'recorded_at':1}}, {'x': {'kind':'maybe_later', 'recorded_at':True}},
                        {'bad id': {'kind':'not_interested', 'recorded_at':1}},
                        {f'goal_{i}': {'kind':'not_interested','recorded_at':1} for i in range(101) }]:
                payload['recommendation_feedback'] = bad
                self.assertEqual(client.post('/v1/goals/evaluate', json=payload).status_code, 422)
