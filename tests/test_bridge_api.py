"""Optional API tests: install .[api,api-test] before running this module."""
import copy
import json
from pathlib import Path
import unittest

try:
    import fastapi
    import httpx
except ImportError:
    raise unittest.SkipTest('Install .[api,api-test] to run optional bridge tests')

from fastapi.testclient import TestClient
from beginner_companion.api.app import app
from beginner_companion.api.models import EvaluationRequest, EvaluationResponse, ErrorResponse

CONTRACTS = Path(__file__).resolve().parents[1] / "contracts"


class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.request = json.loads((CONTRACTS / "fixtures/missing.request.json").read_text())

    def test_health(self):
        self.assertEqual(self.client.get('/v1/health').json()['status'], 'ok')

    def test_shared_fixtures(self):
        for name in ('missing', 'satisfied', 'unknown'):
            with self.subTest(name=name):
                request = json.loads((CONTRACTS / f'fixtures/{name}.request.json').read_text())
                expected = json.loads((CONTRACTS / f'fixtures/{name}.response.json').read_text())
                response = self.client.post('/v1/evaluate', json=request)
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json(), expected)

    def test_boundaries_and_in_progress(self):
        for level, expected in ((39, 'missing'), (40, 'satisfied'), (99, 'satisfied'), (None, 'unknown')):
            self.request['account']['skill_levels']['defence'] = level
            self.request['account']['quest_progress']['dragon_slayer_i'] = 'in_progress'
            response = self.client.post('/v1/evaluate', json=self.request).json()
            self.assertEqual(response['requirements'][0]['status'], expected)
            self.assertEqual(response['requirements'][1]['status'], 'missing')

    def test_invalid_inputs(self):
        for level in (True, 0, 100, 40.5, '40'):
            request = copy.deepcopy(self.request)
            request['account']['skill_levels']['defence'] = level
            response = self.client.post('/v1/evaluate', json=request)
            self.assertEqual(response.status_code, 422)
            self.assertEqual(response.json()['error']['code'], 'invalid_request')
        for key, value in [('protocol_version', True), ('revision', -1), ('revision', True),
                           ('session_id', ''), ('account', None)]:
            request = {**self.request, key: value}
            self.assertEqual(self.client.post('/v1/evaluate', json=request).status_code, 422)
        self.assertEqual(self.client.post('/v1/evaluate', content='{').status_code, 422)
        self.assertEqual(self.client.post('/v1/evaluate', json={**self.request, 'extra': 1}).status_code, 422)

    def test_domain_errors(self):
        for key, value, status, code in [('protocol_version', 2, 409, 'version_mismatch'),
                                         ('goal_id', 'other', 404, 'unknown_goal')]:
            response = self.client.post('/v1/evaluate', json={**self.request, key: value})
            self.assertEqual(response.status_code, status)
            self.assertEqual(response.json()['error']['code'], code)

    def test_schemas_are_current(self):
        for name, model in [('evaluation-request', EvaluationRequest),
                            ('evaluation-response', EvaluationResponse), ('error-response', ErrorResponse)]:
            self.assertEqual(json.loads((CONTRACTS / f'{name}.schema.json').read_text()), model.model_json_schema())


if __name__ == '__main__':
    unittest.main()
