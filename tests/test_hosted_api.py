import asyncio
import os
import subprocess
import sys
import unittest
from fastapi.testclient import TestClient
from beginner_companion.api.app import app
from beginner_companion.api.limits import RequestLimits, MAX_REQUEST_BYTES


class HostedApiTests(unittest.TestCase):
    def test_responses_are_not_cacheable_and_health_reports_current_catalogue(self):
        with TestClient(app) as client:
            health = client.get('/v1/health')
            catalogue = client.get('/v1/catalog')
            self.assertEqual(health.json()['dataset_version'], catalogue.json()['dataset_version'])
            for response in [health, catalogue, client.post('/v1/goals/evaluate', json={})]:
                self.assertEqual(response.headers['cache-control'], 'no-store')

    def test_oversized_request_is_rejected_without_echoing_payload(self):
        with TestClient(app) as client:
            response = client.post('/v1/goals/evaluate', content=b'private-marker' * MAX_REQUEST_BYTES)
            self.assertEqual(response.status_code, 413)
            self.assertNotIn('private-marker', response.text)
            self.assertEqual(response.headers['cache-control'], 'no-store')

    def test_chunked_or_underdeclared_request_cannot_bypass_limit(self):
        async def check(headers):
            messages = iter([{'type': 'http.request', 'body': b'x' * MAX_REQUEST_BYTES, 'more_body': True},
                             {'type': 'http.request', 'body': b'x', 'more_body': False}])
            output = []
            async def receive(): return next(messages)
            async def send(message): output.append(message)
            async def downstream(*args): self.fail('Oversized body reached the application')
            await RequestLimits(downstream)({'type': 'http', 'method': 'POST', 'headers': headers}, receive, send)
            self.assertEqual(output[0]['status'], 413)
        for headers in [[], [(b'content-length', b'1')]]:
            asyncio.run(check(headers))

    def test_production_mode_disables_interactive_documentation(self):
        env = dict(os.environ, COMPANION_ENV='production')
        result = subprocess.run([sys.executable, '-c',
            'from beginner_companion.api.app import app; '
            'assert app.docs_url is None and app.openapi_url is None and app.redoc_url is None'],
            env=env, capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
