"""Real Java -> HTTP -> Python checks; leaves no running service behind."""
import json
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run():
    jar = ROOT / 'bridge-java/lib/gson-2.11.0.jar'
    build = ROOT / 'bridge-java/build'
    build.mkdir(exist_ok=True)
    subprocess.run(['javac', '--release', '11', '-cp', str(jar), '-d', str(build),
                    *[str(p) for p in (ROOT / 'bridge-java/src').rglob('*.java')]], check=True)
    import os
    command = ['java', '-cp', os.pathsep.join((str(build), str(jar))), 'CompanionCaller']
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    url = f'http://127.0.0.1:{port}'
    with tempfile.TemporaryFile(mode='w+b') as log:
        server = subprocess.Popen([sys.executable, '-m', 'uvicorn', 'beginner_companion.api.app:app',
                                   '--host', '127.0.0.1', '--port', str(port)], cwd=ROOT,
                                  stdout=log, stderr=log)
        try:
            for _ in range(100):
                try:
                    with urllib.request.urlopen(url + '/v1/health', timeout=0.5) as response:
                        assert json.load(response)['status'] == 'ok'
                    break
                except (OSError, urllib.error.URLError):
                    if server.poll() is not None:
                        log.seek(0)
                        raise RuntimeError(log.read().decode())
                    time.sleep(0.1)
            else:
                raise RuntimeError('Python server did not become ready')
            for name in ('missing', 'satisfied', 'unknown'):
                result = subprocess.run(command + [str(ROOT / f'contracts/fixtures/{name}.request.json'), url],
                                        capture_output=True, text=True, timeout=15)
                assert result.returncode == 0, result.stderr
                assert result.stdout.count(name + ': ') == 2, result.stdout
                print(f'PASS Java -> Python -> Java: {name}')
            for name in ('fresh', 'partial', 'complete', 'unclaimed', 'unknown'):
                result = subprocess.run(command + [str(ROOT / f'contracts/fixtures/goals-{name}.request.json'), url],
                                        capture_output=True, text=True, timeout=15)
                assert result.returncode == 0, result.stderr
                for goal in ('wear_rune_platebody', 'ardougne_cloak_1', 'prince_ali_rescue'):
                    assert goal + ': ' in result.stdout, result.stdout
                expected = {'fresh': 0, 'partial': 6, 'complete': 15, 'unclaimed': 13, 'unknown': 0}[name]
                assert result.stdout.count(': completed') == expected, result.stdout
                print(f'PASS Java goal catalogue: {name}')
            for name, expected in [('combat', 'wear_rune_platebody'), ('exploration', 'ardougne_cloak_1'),
                                   ('short', 'prince_ali_rescue')]:
                result = subprocess.run(command + [str(ROOT / f'contracts/fixtures/preferences-{name}.request.json'), url],
                                        capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=15)
                assert result.returncode == 0, result.stderr
                assert 'Recommendation: ' + expected in result.stdout, result.stdout
                assert 'Matches your interest' in result.stdout, result.stdout
                print(f'PASS Java preference recommendation: {name}')
            with tempfile.TemporaryDirectory() as temp:
                fixture = Path(temp) / 'request.json'
                history_request = json.loads((ROOT / 'contracts/fixtures/preferences-combat.request.json').read_text())
                history_request['completion_history'] = [{'goal_id': 'attack_20', 'completed_at': int(time.time())}]
                fixture.write_text(json.dumps(history_request), encoding='utf-8')
                result = subprocess.run(command + [str(fixture), url], capture_output=True, text=True, timeout=15)
                assert result.returncode == 0, result.stderr
                assert 'Recommendation: attack_40' in result.stdout, result.stdout
                assert 'recently completing' in result.stdout, result.stdout
                print('PASS Java completion-history recommendation')
                request = json.loads((ROOT / 'contracts/fixtures/missing.request.json').read_text())
                for changes, expected in [({'protocol_version': 2}, 'version_mismatch'),
                                          ({'goal_id': 'other'}, 'unknown_goal'),
                                          ({'revision': -1}, 'invalid_request')]:
                    fixture.write_text(json.dumps({**request, **changes}), encoding='utf-8')
                    result = subprocess.run(command + [str(fixture), url], capture_output=True,
                                            text=True, timeout=15)
                    assert result.returncode == 1 and expected in result.stderr, result
                    print(f'PASS Java structured error: {expected}')
        finally:
            server.terminate()
            try:
                server.wait(timeout=5)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=5)
    result = subprocess.run(command + [str(ROOT / 'contracts/fixtures/missing.request.json'), url],
                            capture_output=True, text=True, timeout=15)
    assert result.returncode == 1 and 'Bridge failed:' in result.stderr, result
    print('PASS unavailable service: nonzero exit and readable error')


if __name__ == '__main__':
    run()
