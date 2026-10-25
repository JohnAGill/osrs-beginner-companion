"""Build and exercise the real Linux image with fictional fixtures only.

Requires Docker Engine. Uses an ephemeral loopback port, removes its test container,
and writes a non-personal build report under ignored build/. No AWS access required.
"""
import argparse
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def docker(*args, capture=True):
    result = subprocess.run(['docker', *args], cwd=ROOT, check=True,
                            capture_output=capture, text=True, timeout=900)
    if not capture:
        return None
    return (result.stdout + (result.stderr if args[0] == 'logs' else '')).strip()


def request(origin, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(origin + path, data=data,
                                 headers={'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as response:
        return response.code, {key.lower(): value for key, value in response.headers.items()}, response.read()
    with response:
        return response.status, {key.lower(): value for key, value in response.headers.items()}, response.read()


def run(tag, skip_build=False):
    if not skip_build:
        docker('build', '--platform', 'linux/amd64', '-t', tag, '.', capture=False)
    image = json.loads(docker('image', 'inspect', tag))[0]
    assert image['Config']['User'] == '10001', 'Container must run as the unprivileged app user'
    name = 'companion-verify-' + uuid.uuid4().hex[:12]
    container = docker('run', '-d', '--name', name, '--memory', '512m', '--cpus', '0.25',
                       '--read-only', '--tmpfs', '/tmp:rw,noexec,nosuid,size=16m',
                       '-p', '127.0.0.1::8000', tag)
    report = {'image_id': image['Id'], 'platform': image['Os'] + '/' + image['Architecture'],
              'memory_limit_mib': 512, 'cpu_limit': 0.25, 'user': '10001', 'checks': []}
    try:
        ports = json.loads(docker('inspect', container))[0]['NetworkSettings']['Ports']['8000/tcp']
        assert len(ports) == 1 and ports[0]['HostIp'] == '127.0.0.1'
        origin = 'http://127.0.0.1:' + ports[0]['HostPort']
        started = time.monotonic()
        while time.monotonic() - started < 60:
            try:
                status, headers, raw = request(origin, '/v1/health')
                if status == 200:
                    break
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(.25)
        else:
            raise RuntimeError('Container did not become ready within 60 seconds')
        report['startup_seconds'] = round(time.monotonic() - started, 3)
        report['dataset_version'] = json.loads(raw)['dataset_version']
        assert headers.get('cache-control') == 'no-store'
        report['checks'].append('health and uncached responses')
        for prefix, names in [('goals', ['fresh', 'partial', 'complete', 'unclaimed', 'unknown']),
                              ('preferences', ['combat', 'exploration', 'short'])]:
            for name in names:
                base = ROOT / 'contracts' / 'fixtures' / (prefix + '-' + name)
                payload = json.loads(base.with_suffix('.request.json').read_text(encoding='utf-8'))
                expected = json.loads(base.with_suffix('.response.json').read_text(encoding='utf-8'))
                status, headers, raw = request(origin, '/v1/goals/evaluate', payload)
                assert status == 200 and json.loads(raw) == expected, 'Fixture mismatch: ' + name
                assert headers.get('cache-control') == 'no-store'
        report['checks'].append('all eight catalogue/preference contract fixtures')
        for path in ['/docs', '/redoc', '/openapi.json']:
            assert request(origin, path)[0] == 404
        assert request(origin, '/v1/goals/evaluate', {'oversized': 'x' * (128 * 1024)})[0] == 413
        report['checks'].append('production docs disabled and oversized bodies rejected')

        payload = json.loads((ROOT / 'contracts/fixtures/goals-partial.request.json').read_text())
        def evaluate(_):
            began = time.monotonic()
            code, _, raw = request(origin, '/v1/goals/evaluate', payload)
            assert code == 200 and len(json.loads(raw)['goals']) == 33
            return time.monotonic() - began
        with ThreadPoolExecutor(max_workers=4) as pool:
            timings = list(pool.map(evaluate, range(40)))
        report['checks'].append('40 evaluations with four concurrent clients')
        report['sample_max_seconds'] = round(max(timings), 3)
        report['sample_mean_seconds'] = round(sum(timings) / len(timings), 3)
        report['docker_memory_sample'] = docker('stats', '--no-stream', '--format', '{{.MemUsage}}', container)
        logs = docker('logs', container)
        assert 'demo-session' not in logs and 'quest_progress' not in logs, 'Request data found in logs'
        report['checks'].append('no fixture payloads in application logs')
        output = ROOT / 'build/container-verification.json'
        output.parent.mkdir(exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(report, indent=2))
        print('PASS: container validated; temporary container removed on exit.')
    except BaseException:
        print(docker('logs', '--tail', '25', container))
        raise
    finally:
        docker('rm', '-f', container)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', default='beginner-companion:staging-test')
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()
    run(args.tag, args.skip_build)
