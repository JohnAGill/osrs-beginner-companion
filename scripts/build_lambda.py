"""Build a Linux/Python 3.12 ZIP for Lambda. Run inside the project's Docker image."""
import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


def build(root):
    root = Path(root)
    output = root / 'build/lambda'
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='companion-lambda-') as temp:
        target = Path(temp)
        subprocess.run([sys.executable, '-m', 'pip', 'install', '--no-compile', '--target', str(target),
                        '-r', str(root / 'deploy/lambda-requirements.txt')], check=True)
        import shutil
        shutil.copytree(root / 'beginner_companion', target / 'beginner_companion',
                        ignore=shutil.ignore_patterns('__pycache__', '*.pyc'))
        shutil.copytree(root / 'LICENSES', target / 'LICENSES')
        for name in ['LICENSE', 'THIRD_PARTY_NOTICES.md']:
            shutil.copy2(root / name, target / name)
        archive = output / 'planner.zip'
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
            for path in sorted(target.rglob('*')):
                if path.is_file() and '__pycache__' not in path.parts and path.suffix != '.pyc':
                    bundle.write(path, path.relative_to(target))
        report = {'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
                  'compressed_bytes': archive.stat().st_size, 'runtime': 'python3.12',
                  'architecture': 'x86_64'}
        (output / 'artifact.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report, indent=2))


if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1])
