"""Prepare a private artifact and reviewable Lambda change set; execute explicitly.

Preparation creates S3 storage and a CloudFormation change set, not a public Lambda.
AWS identifiers are recorded only under ignored data/private/. Requires AWS CLI sign-in.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
PLAN = ROOT / 'data/private/lambda-staging-plan.json'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', default='beginner-companion')
    parser.add_argument('--region', default='eu-west-2')
    parser.add_argument('--aws', default=shutil.which('aws') or str(Path.home() / '.local/bin/aws'))
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--prepare', action='store_true')
    action.add_argument('--execute', action='store_true')
    args = parser.parse_args()

    def aws(*command):
        result = subprocess.run([args.aws, *command, '--profile', args.profile, '--region', args.region,
                                 '--output', 'json', '--no-cli-pager'], capture_output=True, text=True, timeout=180)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return json.loads(result.stdout) if result.stdout.strip() else None

    account = aws('sts', 'get-caller-identity')['Account']
    archive = ROOT / 'build/lambda/planner.zip'
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    if args.execute:
        plan = json.loads(PLAN.read_text(encoding='utf-8'))
        if (plan['account'] != account or plan['region'] != args.region or plan['sha256'] != digest):
            raise RuntimeError('Account, region or artifact changed; prepare a new change set.')
        capacity = aws('lambda', 'get-account-settings')['AccountLimit']['UnreservedConcurrentExecutions']
        if capacity < 102:
            raise RuntimeError('Quota has not reached the capacity needed for a two-execution reservation.')
        aws('cloudformation', 'execute-change-set', '--change-set-name', plan['change_set'])
        print('Change set submitted. Check stack events until creation completes. No cap was removed.')
        return

    bucket = f'beginner-companion-artifacts-{account}-{args.region}'
    try:
        aws('s3api', 'head-bucket', '--bucket', bucket, '--expected-bucket-owner', account)
    except RuntimeError as error:
        if '404' not in str(error) and 'Not Found' not in str(error):
            raise
        command = ['s3api', 'create-bucket', '--bucket', bucket]
        if args.region != 'us-east-1':
            command += ['--create-bucket-configuration', json.dumps({'LocationConstraint': args.region})]
        aws(*command)
    aws('s3api', 'put-public-access-block', '--bucket', bucket, '--public-access-block-configuration',
        json.dumps(dict(BlockPublicAcls=True, IgnorePublicAcls=True, BlockPublicPolicy=True, RestrictPublicBuckets=True)))
    aws('s3api', 'put-bucket-encryption', '--bucket', bucket, '--server-side-encryption-configuration',
        json.dumps({'Rules': [{'ApplyServerSideEncryptionByDefault': {'SSEAlgorithm': 'AES256'}}]}))
    key = f'lambda/{digest}/planner.zip'
    aws('s3api', 'put-object', '--bucket', bucket, '--key', key, '--body', str(archive),
        '--content-type', 'application/zip', '--server-side-encryption', 'AES256')
    stack = 'beginner-companion-staging'
    kind = 'CREATE'
    try:
        current = aws('cloudformation', 'describe-stacks', '--stack-name', stack)['Stacks'][0]
        if current['StackStatus'] != 'REVIEW_IN_PROGRESS':
            kind = 'UPDATE'
    except RuntimeError as error:
        if 'does not exist' not in str(error):
            raise
    created = aws('cloudformation', 'create-change-set', '--stack-name', stack,
        '--change-set-name', 'planner-' + digest[:12] + '-' + uuid.uuid4().hex[:8],
        '--change-set-type', kind, '--template-body', 'file://' + str(ROOT / 'deploy/lambda-template.json'),
        '--capabilities', 'CAPABILITY_IAM', '--parameters', json.dumps([
            {'ParameterKey': 'ArtifactBucket', 'ParameterValue': bucket},
            {'ParameterKey': 'ArtifactKey', 'ParameterValue': key},
            {'ParameterKey': 'ReservedConcurrency', 'ParameterValue': '2'}]))
    plan = dict(account=account, region=args.region, bucket=bucket, key=key, sha256=digest,
                stack=stack, change_set=created['Id'])
    PLAN.parent.mkdir(parents=True, exist_ok=True)
    PLAN.write_text(json.dumps(plan, indent=2) + '\n', encoding='utf-8')
    print('Prepared encrypted private artifact and change set; public Lambda not created.')
    print('Plan stored in ignored data/private/lambda-staging-plan.json.')


if __name__ == '__main__':
    main()
