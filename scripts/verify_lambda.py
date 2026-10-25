"""Exercise the built ZIP in AWS's Python 3.12 Lambda runtime image; no AWS account needed."""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = 'public.ecr.aws/lambda/python:3.12'


def event(path, payload=None, encoded=False):
    body = '' if payload is None else json.dumps(payload)
    return {'version': '2.0', 'routeKey': '$default', 'rawPath': path,
            'rawQueryString': '', 'headers': {'host': 'example.lambda-url.eu-west-2.on.aws',
                                             'content-type': 'application/json'},
            'requestContext': {'accountId': 'anonymous', 'apiId': 'example',
                              'domainName': 'example.lambda-url.eu-west-2.on.aws',
                              'requestId': 'synthetic-request', 'routeKey': '$default',
                              'stage': '$default', 'time': '', 'timeEpoch': 0,
                              'http': {'method': 'GET' if payload is None else 'POST',
                                       'path': path, 'protocol': 'HTTP/1.1',
                                       'sourceIp': '192.0.2.1', 'userAgent': 'synthetic-test'}},
            'body': base64.b64encode(body.encode()).decode() if encoded else body,
            'isBase64Encoded': encoded}


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True, timeout=300).strip()


def verify(archive):
    name = 'companion-lambda-' + uuid.uuid4().hex[:12]
    with tempfile.TemporaryDirectory(prefix='companion-lambda-runtime-') as temp:
        target = Path(temp)
        target.chmod(0o755)
        with zipfile.ZipFile(archive) as package:
            package.extractall(target)
        docker('pull', RUNTIME)
        container = docker('run', '-d', '--name', name, '--platform', 'linux/amd64',
                           '--memory', '512m', '--cpus', '0.25', '--user', '10001:10001',
                           '--read-only', '--tmpfs', '/tmp:rw,size=32m',
                           '--mount', f'type=bind,source={target},target=/var/task,readonly',
                           '-p', '127.0.0.1::8080', RUNTIME,
                           'beginner_companion.api.lambda_handler.handler')
        try:
            port = json.loads(docker('inspect', container))[0]['NetworkSettings']['Ports']['8080/tcp'][0]['HostPort']
            url = f'http://127.0.0.1:{port}/2015-03-31/functions/function/invocations'
            def invoke(payload):
                request = urllib.request.Request(url, json.dumps(payload).encode(), {'Content-Type': 'application/json'})
                with urllib.request.urlopen(request, timeout=30) as response:
                    return json.load(response)
            for _ in range(60):
                try:
                    health = invoke(event('/v1/health'))
                    assert health['statusCode'] == 200, health
                    break
                except urllib.error.URLError:
                    time.sleep(.5)
            else:
                raise RuntimeError('Lambda runtime did not become ready')
            for prefix, names in [('goals', ['fresh', 'partial', 'complete', 'unclaimed', 'unknown']),
                                  ('preferences', ['combat', 'exploration', 'short'])]:
                for name in names:
                    fixture = ROOT / 'contracts/fixtures' / f'{prefix}-{name}'
                    payload = json.loads(fixture.with_suffix('.request.json').read_text(encoding='utf-8'))
                    expected = json.loads(fixture.with_suffix('.response.json').read_text(encoding='utf-8'))
                    response = invoke(event('/v1/goals/evaluate', payload, encoded=True))
                    assert response['statusCode'] == 200 and json.loads(response['body']) == expected, name
                    assert response['headers']['cache-control'] == 'no-store'
            assert invoke(event('/docs'))['statusCode'] == 404
            assert invoke(event('/v1/goals/evaluate', {'oversized': 'x' * (128 * 1024)}))['statusCode'] == 413
            print('PASS: AWS Lambda Python 3.12 runtime, all eight fixtures, warm account isolation, production docs and body limits.')
        except BaseException:
            subprocess.run(['docker', 'logs', '--tail', '30', container], check=False)
            raise
        finally:
            docker('rm', '-f', container)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=ROOT / 'build/lambda/planner.zip')
    args = parser.parse_args()
    verify(args.archive)
