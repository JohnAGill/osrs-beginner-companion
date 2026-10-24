import unittest
try:
    from fastapi.testclient import TestClient
    from beginner_companion.api.app import app
except ImportError:
    TestClient = None


@unittest.skipIf(TestClient is None, 'Install API dependencies')
class LiveAccountApiTests(unittest.TestCase):
    def test_live_observations_are_accepted_and_unknowns_stay_unknown(self):
        payload = dict(protocol_version=1, session_id='synthetic-live-session', revision=1,
                       source='live', account=dict(members=False, quest_points=0,
                       skill_levels=dict(defence=1, thieving=1, sailing=1, attack=1),
                       skill_xp=dict(defence=0, thieving=0, sailing=0, attack=0),
                       quest_progress=dict(prince_ali_rescue='not_started')))
        with TestClient(app) as client:
            response = client.post('/v1/goals/evaluate', json=payload)
            self.assertEqual(response.status_code, 200, response.text)
            data = response.json()
            self.assertIn('Live character', data['notes'][0])
            self.assertNotIn('Fictional', data['notes'][0])
            goals = {row['goal']['id']: row for row in data['goals']}
            self.assertEqual(goals['wear_rune_platebody']['status'], 'unknown')
            self.assertEqual(goals['ardougne_cloak_1']['status'], 'unknown')
            self.assertEqual(goals['prince_ali_rescue']['status'], 'incomplete')
            payload['revision'] = 2
            payload['account']['quest_progress']['prince_ali_rescue'] = 'completed'
            response = client.post('/v1/goals/evaluate', json=payload).json()
            goal = next(row for row in response['goals'] if row['goal']['id'] == 'prince_ali_rescue')
            self.assertEqual(goal['status'], 'completed')
            self.assertEqual(response['revision'], 2)

    def test_bad_xp_and_identity_fields_are_rejected(self):
        with TestClient(app) as client:
            for account in (dict(skill_xp={'attack': -1}), dict(skill_xp={'attack': True}),
                            dict(skill_xp={'invented_skill': 0}), dict(username='never-send-me')):
                payload = dict(protocol_version=1, session_id='synthetic', revision=1, source='live', account=account)
                self.assertEqual(client.post('/v1/goals/evaluate', json=payload).status_code, 422)
