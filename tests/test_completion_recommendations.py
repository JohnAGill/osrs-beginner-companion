import copy
import unittest
from beginner_companion.domain.catalog import load_catalog, evaluate_goals
from beginner_companion.domain.recommendations import recommend
from beginner_companion.api.app import app
from fastapi.testclient import TestClient


class CompletionRecommendationTests(unittest.TestCase):
    NOW = 1800000000

    def result(self, history, interests=(), members=True, account=None):
        account = account or {'members': members, 'skill_levels': {'attack': 20, 'magic': 25},
                              'quest_progress': {'plague_city': 'completed'}}
        catalog = load_catalog()
        return recommend(catalog, evaluate_goals(catalog, account),
                         {'interests': list(interests), 'commitment': 'any'}, account, history, now=self.NOW)

    def event(self, goal, days=0):
        return {'goal_id': goal, 'completed_at': self.NOW - days * 86400}

    def test_recent_followup_and_preferences_both_explained(self):
        result = self.result([self.event('attack_20')], ['combat'])
        self.assertEqual(result['recommended_goal_id'], 'attack_40')
        reasons = ' '.join(result['goals'][0]['recommendation']['reasons'])
        self.assertIn('Matches your interest in combat', reasons)
        self.assertIn('recently completing', reasons)
        self.assertEqual(self.result([self.event('magic_25')])['recommended_goal_id'], 'magic_37')
        # An explicit exploration preference does not inherit a combat follow-up bonus.
        different = self.result([self.event('attack_20')], ['exploration'])
        attack = next(g for g in different['goals'] if g['goal']['id'] == 'attack_40')
        self.assertFalse(any('recently completing' in r for r in attack['recommendation']['reasons']))

    def test_expired_future_unknown_and_unconfirmed_history_is_ignored(self):
        baseline = self.result([])
        for history in [[self.event('attack_20', 31)], [self.event('attack_20', -1)],
                        [self.event('invented')], [self.event('prayer_43')]]:
            self.assertEqual(self.result(history), baseline)
        duplicate = [self.event('attack_20'), self.event('attack_20', 5)]
        self.assertEqual(self.result(duplicate), self.result([self.event('attack_20')]))

    def test_membership_completion_and_recency_order(self):
        blocked = self.result([self.event('plague_city')], members=False)
        cloak = next(g for g in blocked['goals'] if g['goal']['id'] == 'ardougne_cloak_1')
        self.assertFalse(cloak['recommendation']['recommended'])
        recent = self.result([self.event('magic_25', 10), self.event('attack_20', 1)])
        self.assertEqual(recent['recommended_goal_id'], 'attack_40')
        a = {'members': True, 'skill_levels': {'attack': 40}}
        self.assertNotEqual(self.result([self.event('attack_20')], account=a)['recommended_goal_id'], 'attack_40')

    def test_wire_validation_and_no_mutation(self):
        history = [self.event('attack_20')]
        before = copy.deepcopy(history)
        self.result(history)
        self.assertEqual(history, before)
        payload = dict(protocol_version=1, session_id='history-test', revision=1, account={}, completion_history=history)
        with TestClient(app) as client:
            self.assertEqual(client.post('/v1/goals/evaluate', json=payload).status_code, 200)
            for bad in [[{'goal_id':'attack_20','completed_at':True}], [{'goal_id':'attack_20','completed_at':-1}], history * 51]:
                payload['completion_history'] = bad
                self.assertEqual(client.post('/v1/goals/evaluate', json=payload).status_code, 422)
