# Running without a separately managed Python service

Updated 2026-10-25: the selected path is a **hosted Python API**. HTTPS configuration,
explicit consent and deployment preparation are implemented; no public service is deployed.
The selected deployment is [AWS Lambda with a Function URL](lambda-deployment.md).
See [the shared hosted transport implementation](hosted-service.md). Local development retains
127.0.0.1:8765. The comparison below records the alternatives.

| Path | Player setup | Main work/tradeoff |
| --- | --- | --- |
| Bundled local helper | Install one companion application; it starts packaged Python | Per-OS executable packaging, updates, process lifecycle, local endpoint ownership; not suitable for Plugin Hub: subprocess execution is forbidden |
| Hosted Python API | Plugin connects to a service; no local Python installation | Hosting, HTTPS, availability, privacy, version compatibility, rate limiting and an offline experience |
| Java evaluator | Install the plugin only | Port evaluation/matching rules to Java; Python can remain an authoring/validation tool |

Bundling Python usually means a companion executable containing an interpreter and its
dependencies. It is still a separate native process, even if the plugin starts it. Embedding
a Python runtime inside a plugin JAR adds substantial dependency/platform complexity and
is not the recommended first distribution experiment. RuneLite explicitly forbids plugins from launching external programs; this excludes a
plugin-launched helper from the planned Hub distribution.

For this portfolio project, keeping the Java/Python boundary is useful: run locally during
development and make a hosted API an optional later deployment. For the smoothest eventual
plugin-only experience, consider keeping a small Java fallback or porting the rules to Java.
The current rule engine is small enough that this remains realistic.

## Hosted API implementation path

1. Separate transport configuration from the hard-coded loopback address; retain local mode.
2. Deploy the same stateless evaluation service behind HTTPS, with protocol/catalog versions.
3. Explain what observations leave the computer and make remote mode an explicit choice.
   Send only fields needed for selected features; no credentials, names or local account keys.
4. Reuse snapshot coalescing, timeouts and stale-result rejection. Add bounded retry/backoff,
   caching and an offline state that does not present old observations as current progress.
5. Add request limits and operational logging that avoids account payloads. Document retention.
6. Discuss the complete design and remote behaviour with RuneLite reviewers before treating
   it as a Plugin Hub distribution candidate. Keep recommendation rules/source inspectable.

The current build is not remote-ready: its URL is fixed to loopback, deployment/consent are
not implemented, and local-only assumptions need review before exposing an endpoint.

## Jagex and RuneLite constraints

Hosting location does not by itself determine whether a feature is acceptable. Jagex's
published third-party client guidelines constrain feature behaviour (such as automation
and prohibited combat assistance). Interpreting those rules, a stateless goal recommendation
service is not inherently a prohibited feature solely because it is hosted. This is an
interpretation, not Jagex approval of this project or architecture.

RuneLite separately reviews Plugin Hub submissions and says it will not merge a plugin if
it is difficult to verify compliance. Remote logic and native helpers therefore need an
early review discussion; a compliant concept is not a guarantee of acceptance.

Sources checked 2026-10-24:

- https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1
- https://github.com/runelite/plugin-hub#reviewing
- https://github.com/runelite/plugin-hub#third-party-dependencies
