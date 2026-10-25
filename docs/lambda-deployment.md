# AWS Lambda deployment

Selected 2026-10-25 in place of the proposed Lightsail service. No Lightsail service
is required. The stateless FastAPI application is adapted by pinned Mangum 0.22.0;
ordinary local Uvicorn and Docker development remain supported.

## Verified artifact

The Linux/Python 3.12 ZIP was built and exercised inside AWS's official Python 3.12
Lambda runtime image, constrained to 512 MiB and 0.25 CPU, as UID 10001 with a read-only
filesystem. All eight catalogue/preference fixtures matched exactly through Function URL
v2 events, including base64 bodies and repeated invocations for different demo accounts.
The runtime checks also confirm disabled docs, no-store responses and request-size limits.
This validates the artifact locally, not real AWS network latency or account permissions.

Build from a Linux/amd64 Python 3.12 environment with pip:

```sh
python scripts/build_lambda.py
python scripts/verify_lambda.py
```

The builder installs dependencies into an isolated temporary directory and emits
`build/lambda/planner.zip` plus `artifact.json` with SHA-256 and runtime information.
Build this in the existing Linux Docker image, not Windows: Pydantic includes a native
Linux extension. The runtime verifier requires Docker, pulls AWS's runtime image, mounts
the unpacked ZIP, invokes fictional events and removes its temporary container.

The current ZIP is roughly 3.5 MB compressed. Build outputs are ignored and contain no
account state. Source changes require rebuilding and re-verifying the ZIP before upload.

## Staging stack

`deploy/lambda-template.json` is ordinary CloudFormation, without a SAM/CDK requirement:

- Python 3.12, x86_64, 512 MiB, eight-second execution timeout.
- Reserved concurrency of two by default; no provisioned concurrency or warm instances.
- A public HTTPS Function URL with buffered JSON responses, no API Gateway or CORS settings.
- Both current invocation permissions, constrained to URL use where applicable.
- A role restricted to writing its own pre-created CloudWatch log group; seven-day retention.
- No database, VPC, NAT gateway, user credentials, secret in the plugin or gameplay actions.

The template takes a private artifact bucket and immutable object key, plus the function
name (default `beginner-companion-staging`) and reserved concurrency. It does not create
the artifact bucket. Keep bucket names, account identity and generated deployment outputs
in ignored private files rather than committing them.

AWS [requires both invocation permissions for new Function URLs](https://docs.aws.amazon.com/lambda/latest/dg/urls-auth.html).
The endpoint is deliberately public because the distributed RuneLite plugin cannot contain
a private AWS signing key. The plugin's opt-in and payload disclosure still apply.

## Deployment sequence

1. Complete `aws login --profile beginner-companion --region eu-west-2` in the browser.
   Use temporary credentials; do not put passwords, authorization codes or keys in chat.
2. Confirm the account identity and inspect `aws lambda get-account-settings`. AWS reserves
   100 units of unreserved concurrency; some new accounts have reduced quotas and cannot
   yet allocate this template's reservation. Resolve the quota before deployment rather
   than silently removing the concurrency control.
3. Validate the template using `aws cloudformation validate-template`.
4. Create or select a private S3 artifact bucket in the same region. Block public access,
   use encryption, and upload the verified ZIP under a key containing its SHA-256.
5. Review a CloudFormation change set for `beginner-companion-staging` with the template's
   artifact parameters and `CAPABILITY_IAM`, then execute it. Record the resulting URL
   and artifact hash locally.
6. Smoke-test health and fictional fixtures through the real HTTPS URL. Measure cold and
   warm latency against the plugin's ten-second total deadline. Verify logs do not contain
   request payloads. Use no real account data for this first test.
7. Launch the dev client with `-PhostedPlanner`, configure the returned origin, and enable
   sharing explicitly. Test live progress, opting out, account switches and outages.

No hosted address is configured by default. An AWS budget alert and cost monitoring should
be configured before broader use. Reserved concurrency limits simultaneous work, not total
monthly requests or spend. A Function URL has no extra endpoint fee, but requests, execution,
outbound transfer, artifact storage and logs can incur usage charges. See
[Lambda pricing](https://aws.amazon.com/lambda/pricing/).

For rollback, keep the previous verified ZIP and deploy its immutable S3 key through the
same stack. To stop invocations urgently, set reserved concurrency to zero. Deleting the
stack removes its function, public endpoint, role and log group; artifacts in the separate
bucket remain and need deliberate cleanup.

## Remaining release checks

AWS sign-in and template validation are complete. The verified ZIP is uploaded to an
encrypted private S3 bucket with public access blocked. An AWS-reviewed CREATE change
set is ready but **not executed**; it contains only the six resources listed above.
Actual account/bucket/change-set identifiers are in ignored
`data/private/lambda-staging-plan.json`. No Lambda or public endpoint has been created.

The account currently has concurrency 10. A user-approved request for 1,001 is in
`CASE_OPENED` state with AWS; the quota API rejected values below the standard 1,000.
The planner remains capped at two in the template. Await AWS's quota decision before
executing; do not remove the cap to bypass the account limitation.

`python scripts/deploy_lambda.py --prepare` prepares the private artifact and a new change
set. It performs cloud writes and is not a dry run. `--execute` verifies the current account,
region, artifact hash and concurrency availability before submitting that exact change set.
It never removes the cap. Both require the locally authenticated AWS CLI.

Actual Lambda creation, HTTPS/cold-start tests and real-client validation are still pending. Complete provider privacy/logging verification and monitoring before
public distribution. This deployment does not establish RuneLite Plugin Hub approval;
the packaging/filesystem review described in `hosted-service.md` is still required.
