import json
from pathlib import Path
import unittest

from beginner_companion.api.lambda_handler import handler

FIXTURES = Path(__file__).resolve().parents[1] / 'contracts/fixtures'


from scripts.verify_lambda import event


class LambdaHandlerTests(unittest.TestCase):
    def test_function_url_events_match_all_shared_fixtures(self):
        for prefix, names in [('goals', ['fresh', 'partial', 'complete', 'unclaimed', 'unknown']),
                              ('preferences', ['combat', 'exploration', 'short'])]:
            for name in names:
                with self.subTest(fixture=prefix + '-' + name):
                    payload = json.loads((FIXTURES / f'{prefix}-{name}.request.json').read_text(encoding='utf-8'))
                    expected = json.loads((FIXTURES / f'{prefix}-{name}.response.json').read_text(encoding='utf-8'))
                    result = handler(event('/v1/goals/evaluate', payload), None)
                    self.assertEqual(result['statusCode'], 200)
                    self.assertEqual(json.loads(result['body']), expected)
                    self.assertEqual(result['headers']['cache-control'], 'no-store')

    def test_base64_events_and_warm_invocations_do_not_share_character_state(self):
        for name in ['complete', 'fresh', 'unknown']:
            payload = json.loads((FIXTURES / f'goals-{name}.request.json').read_text(encoding='utf-8'))
            response = handler(event('/v1/goals/evaluate', payload, encoded=True), None)
            expected = json.loads((FIXTURES / f'goals-{name}.response.json').read_text(encoding='utf-8'))
            self.assertEqual(json.loads(response['body']), expected)

    def test_health_and_safe_errors_through_function_url(self):
        self.assertEqual(handler(event('/v1/health'), None)['statusCode'], 200)
        invalid = handler(event('/v1/goals/evaluate', {'private-marker': 'discard'}), None)
        self.assertEqual(invalid['statusCode'], 422)
        self.assertNotIn('private-marker', invalid['body'])
        oversized = handler(event('/v1/goals/evaluate', {'body': 'x' * (128 * 1024)}), None)
        self.assertEqual(oversized['statusCode'], 413)
