"""Pattern-based review of candidate files and every commit reachable from HEAD.

Reports file/rule names, never matched secrets. Does not inspect ignored personal data.
This complements visual screenshot review; it is not proof that arbitrary secrets are absent.
"""
import re
import struct
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RULES = {
    'private key': re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'GitHub credential': re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})'),
    'AWS access key': re.compile(rb'(?:AKIA|ASIA)[A-Z0-9]{16}'),
    'Windows user path': re.compile(rb'[A-Za-z]:[\\/]+Users[\\/]+[^\\/\s"\']+'),
    'email address': re.compile(rb'[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}'),
}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def inspect(name, data):
    issues = []
    path = Path(name)
    if (any(part in {'.venv', '.gradle', '.gradle-local', '__pycache__'} for part in path.parts)
            or name.startswith('data/') or path.suffix in {'.log', '.class'}
            or path.name in {'.env', 'credentials.properties', 'my-account-export.json'}
            or path.name.startswith('account-export')):
        issues.append('private/generated path')
    if path.suffix == '.jar':
        if name != 'plugin/gradle/wrapper/gradle-wrapper.jar': issues.append('unexpected binary')
        return issues
    if path.suffix == '.png':
        if data[:8] != b'\x89PNG\r\n\x1a\n': return issues + ['invalid PNG']
        offset = 8
        while offset + 8 <= len(data):
            length = struct.unpack('>I', data[offset:offset+4])[0]
            kind = data[offset+4:offset+8]
            if kind in {b'tEXt', b'zTXt', b'iTXt', b'eXIf'}: issues.append('image metadata needs review')
            offset += length + 12
        return issues
    for rule, pattern in RULES.items():
        for match in pattern.finditer(data):
            if rule == 'email address' and (name.startswith('LICENSES/') or match[0].endswith(b'@users.noreply.github.com')):
                continue  # Retain upstream copyright notices and GitHub no-reply attribution.
            issues.append(rule)
            break
    return issues


def main():
    findings = []
    names = git('ls-files', '-z', '--cached', '--others', '--exclude-standard').decode().split('\0')
    for name in filter(None, names):
        file = ROOT / name
        if file.is_file():
            findings.extend((name, rule) for rule in inspect(name, file.read_bytes()))
    commits = git('rev-list', 'HEAD').decode().splitlines()
    checked = set()
    for commit in commits:
        for entry in git('ls-tree', '-rz', commit).split(b'\0'):
            if not entry: continue
            metadata, name = entry.split(b'\t', 1)
            oid = metadata.split()[2].decode()
            if oid in checked: continue
            checked.add(oid)
            findings.extend(('history:' + name.decode(), rule) for rule in inspect(name.decode(), git('cat-file', 'blob', oid)))
        identity = git('show', '-s', '--format=%an%x00%ae%x00%cn%x00%ce', commit).decode().strip().split('\0')
        if any(not email.endswith('@users.noreply.github.com') for email in [identity[1], identity[3]]):
            findings.append(('commit:' + commit[:8], 'non-no-reply email'))
        if any(re.search(r'(?i)(DESKTOP|LAPTOP)-|\\', name) for name in [identity[0], identity[2]]):
            findings.append(('commit:' + commit[:8], 'machine identity'))
    if findings:
        for name, rule in sorted(set(findings)): print(f'REVIEW {name}: {rule}')
        raise SystemExit(1)
    print(f'PASS: {len(list(filter(None, names)))} candidate files; {len(commits)} reachable commits; {len(checked)} unique Git blobs.')
    print('No findings from configured patterns, file rules, PNG metadata checks or commit identity checks.')


if __name__ == '__main__':
    main()
