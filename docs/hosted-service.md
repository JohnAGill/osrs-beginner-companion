# Hosted planner: first production milestone

Status: the Linux/amd64 container was built and tested in Ubuntu WSL on 2026-10-25,
as UID 10001 with a read-only filesystem, 512 MiB memory and 0.25 CPU limits.
All eight response fixtures, private response headers, disabled docs, body limits and
a 40-request/four-client smoke test passed. Private artifact storage and an unexecuted Lambda change set are prepared; no Lambda
or public planner endpoint has been created. See the current Lambda deployment status. This is a local smoke test, not an AWS capacity guarantee.

## Architecture and consent

The RuneLite plugin uses the injected OkHttp client and Gson. It sends versioned JSON
to a configured HTTPS origin and validates the returned snapshot before display. It
does not ship, download or launch Python, execute returned code, or expose an HTTP server.
The standalone Java console demo retains its independent Java HTTP transport for local tests.

Production defaults are an empty service URL and **Share progress = false**. Set the
planner's HTTPS origin in RuneLite's Beginner Companion settings, read the disclosure,
then enable sharing. A URL cannot include a path, query, fragment or credentials.
Changing the destination or disabling sharing cancels outstanding calls and clears the
visible result. It does not delete locally saved choices. Production plugin submission
should use a reviewed service origin rather than an unrestricted URL field; agree the
final endpoint and review requirements before release.

Payload: real base skill levels and XP, selected quest/diary observations, observed
equipment/Chronicle milestones, membership status, quest points, preference answers,
completion goal IDs/times, feedback IDs/types/times, and random session/revision values.
Names, account hashes, local character keys, credentials, bank contents and full inventory
contents are excluded. The hosting provider inevitably receives connection metadata such
as IP addresses. Saved goals and tasks stay local. Preferences last for the client session.

The service is stateless and uses no database. Request bodies are not logged by the
application, and the supplied container disables Uvicorn access logs. Provider logging
and retention must be checked separately before publishing a privacy notice.

The client uses asynchronous calls with a 10-second total deadline, no automatic redirects
or transport retries, and a 2 MiB response limit. Only JSON/HTTP 200 responses are accepted;
other responses show a generic failure rather than echoing a remote error page. Hosted live
requests have a five-second minimum interval and coalesce changes. Periodic game observations
flush the latest queued snapshot. Failures back off and keep last confirmed results visibly
stale; stale progress is not used to record completions or supply current overlay progress.
Account/logout/destination changes still clear results rather than retaining another context.

The API rejects bodies larger than 128 KiB, including streamed bodies with no or misleading
Content-Length, without echoing input. Responses use `Cache-Control: no-store`. Health reports
the actual catalogue version; production mode disables interactive docs and OpenAPI routes.
This is a public service: no secret is embedded in the distributed plugin. Browser CORS access
is not enabled because RuneLite is a desktop client.

## Local development

`plugin/gradlew -p plugin run` explicitly sets `companion.devServiceUrl=http://127.0.0.1:8765`
and the existing private demo-state path. That launch opts into the local service; it is
separate from production consent and does not contact a remote host. Its sidebar disclosure
names the local destination. HTTP is accepted only for literal 127.0.0.1 in this development
mode. Add `-PhostedPlanner` to the Gradle run command to remove the loopback override
and test the configured HTTPS origin and explicit consent instead. Run Python as documented
in the main README. Test helpers provide their own clients.

Outside this development launch, saved state is under RuneLite's user directory at
`beginner-companion/character-goals.json`. Existing development saves remain in their original
private file; no automatic copying of personal state is performed.

## Build and smoke-test the container

From the repository root on a machine with Docker:

```sh
docker build -t beginner-companion:local .
docker run --rm -p 127.0.0.1:8000:8000 beginner-companion:local
```

In a separate terminal, inspect `/v1/health` and POST a fictional request fixture to
`http://127.0.0.1:8000/v1/goals/evaluate`. Expect HTTP 200, matching session/revision,
33 goals, the current catalogue version and `Cache-Control: no-store`. `/docs` should
return 404. Confirm request bodies never appear in container logs. The image runs as
UID 10001, has no persistent volume, and limits Uvicorn concurrency to 64. Its build
context allowlist excludes local saves, credentials, Git metadata and development logs.

Dependencies are version-pinned in `deploy/requirements.txt`. Before a public release,
scan dependencies/image and add dependency artifact hashes/provenance. The base image
is pinned by digest and the Linux image is now locally verified. Keep scanning and
refreshing the pin as security updates are released. The container follows [FastAPI's deployment model](https://fastapi.tiangolo.com/deployment/docker/).

## Earlier AWS proposal: Lightsail Containers

Superseded by the selected [AWS Lambda deployment](lambda-deployment.md). No Lightsail
resources were created. The comparison and configuration below are retained for reference.

Reviewed 2026-10-25. Lightsail Containers is a straightforward initial target for this
small stateless API. Its default domain provides HTTPS without buying a domain; container
port 8000 remains HTTP behind the managed TLS endpoint. See [AWS container documentation](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-container-services.html).

[Published prices](https://aws.amazon.com/lightsail/pricing/) start at US$7/month for one
Nano node (512 MB), or US$10/month for Micro (1 GB). Treat these as base prices, not an
all-in quote: verify region, tax, transfer and any additional infrastructure before creating
resources. Measure memory and load before choosing the final size. A billing budget alert
is useful, but is not a hard spending cap. Disabled container services continue to incur
charges; see the [AWS FAQ](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-faq-containers.html).

Do not start with App Runner for a new account: AWS has [closed it to new customers](https://docs.aws.amazon.com/apprunner/latest/dg/apprunner-availability-change.html).
ECS/Fargate is a future option if we need more networking or scaling control, with more
infrastructure to operate and price.

Deployment sequence, after account/region/budget are agreed:

1. Build and locally smoke-test the image; record its revision/digest.
2. Create one Lightsail container service and push the tested image to it.
3. Copy `deploy/lightsail-deployment.example.json`, replace the image placeholder with the
   returned image ID and deploy it. The example does not create resources by itself.
4. Verify HTTPS health and fixture evaluation through the AWS-generated domain. Configure
   that origin in RuneLite and test live opt-in, opt-out, account switching and outages.
5. Confirm rollback to a previous image and establish the service's privacy notice/support
   contact before inviting other users. A custom domain can be added later.

## Remaining release work

- Validate the AWS HTTPS deployment and test sustained load and failure recovery. The local
  container smoke test passed at 512 MiB/0.25 CPU; it does not establish production capacity.
- Set ingress request-rate/body-read limits and abuse controls appropriate to the provider.
  Application body/concurrency bounds and client coalescing do not replace those controls.
  If an edge proxy is added, prevent bypass through the original public endpoint.
- Verify provider logs/retention, add uptime monitoring, a budget alert and rollback procedures.
- Finish Hub packaging: ordinary resource/source layout, latest supported RuneLite compatibility,
  review filesystem persistence against the current Filepath guidance, and provide network disclosure
  metadata. This change alone does not establish Plugin Hub compliance or approval.
- Get maintainer feedback on the exact endpoint, data flow and user-facing functionality.

Policy references: [language/API restrictions](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features#forbidden-language-features),
[connection disclosures](https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub),
and [Hub build guidance](https://github.com/runelite/plugin-hub#build-type).

## Repeatable container verification

`python3 scripts/verify_container.py` builds the image, starts a temporary read-only
container bound only to loopback, runs fictional fixtures and a small concurrency test,
then removes the container. It writes image identity and measurements to ignored
`build/container-verification.json`. It never calls AWS. `--skip-build` reruns checks
against the existing test tag.

On this Windows machine Docker Engine is installed inside the existing Ubuntu WSL
distribution, not as Docker Desktop. From the repository root in PowerShell:

```powershell
wsl -d Ubuntu -u root -- sh -lc 'cd "/mnt/c/path/to/beginner-companion" && python3 scripts/verify_container.py'
```

Replace the example project location with your own WSL-mounted checkout. The daemon
can be started with `wsl -d Ubuntu -u root -- systemctl start docker` if it is stopped.

## One-service staging plan

For the initial portfolio deployment, use one service named `beginner-companion`,
region `eu-west-2` (London), power `nano`, scale `1`. Its creation request is in
`deploy/lightsail-staging-service.json`. Use it as staging while testing; once ready,
release the same service instead of paying for a second permanent environment. A
separate staging and production service at one Nano node each would double the base
price from US$7 to US$14/month, before tax/overages. This plan does not authorize creation.

Before running any resource-creation command, authenticate locally, confirm the AWS
account and region, query `lightsail get-container-service-powers` to verify current
pricing, and obtain approval for the paid resource. Then create with the reviewed JSON,
push the verified image, and apply the deployment example with the exact returned image
ID. Keep actual account details and generated deployment files in ignored private storage.

AWS CLI 2.32+ supports browser-based `aws login --profile beginner-companion --region
eu-west-2` with temporary credentials. Complete authentication in the browser; never
paste access keys or passwords into chat or repository files. See [AWS's sign-in guide](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sign-in.html).
