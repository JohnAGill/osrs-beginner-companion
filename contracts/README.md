# Local bridge protocol v1

Synthetic demo only: no credentials, account exports or game login required.
Python is the authority for evaluation. Java sends observations and renders results.

## Start Python (PowerShell, repository root)

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[api,api-test]"
.\.venv\Scripts\python -m uvicorn beginner_companion.api.app:app --host 127.0.0.1 --port 8765
```

The domain evaluator has no HTTP dependencies. Interactive API docs are
at http://127.0.0.1:8765/docs while the server runs. Stop the foreground server with Ctrl+C.

## Run Java (second PowerShell terminal, repository root)

Requires a JDK 11+ with both java and javac on PATH (tested with JDK 17).
Gson is pinned, downloaded from Maven Central, SHA-256 checked and ignored by Git.
No Maven or Gradle install is required for this standalone milestone.

```powershell
.\bridge-java\setup.ps1
.\bridge-java\run.ps1
.\bridge-java\run.ps1 -Fixture contracts/fixtures/satisfied.request.json
.\bridge-java\run.ps1 -Fixture contracts/fixtures/unknown.request.json
```

First run compiles Java with a Java 11 target. Each call prints Python's requirement rows.
The missing fixture reports both requirements missing; the satisfied fixture reports both
satisfied; the unknown fixture reports both unknown. This is not full goal completion.

Optional port: change Uvicorn's --port and pass -ServiceUrl http://127.0.0.1:8766 to Java.
The caller restricts URLs to IPv4 loopback HTTP and does not follow redirects. It uses a
two-second connect timeout and five-second request timeout. Failure exits 1; usage exits 2.
Retry by running the caller again. Its asynchronous evaluate method can later be reused
behind a UI; only the console main blocks awaiting completion.

## Contract

- GET /v1/health: status, protocol_version, dataset_version.
- POST /v1/evaluate: evaluation-request.schema.json -> evaluation-response.schema.json.
- 422 invalid_request: malformed JSON, invalid values, missing required fields or extras.
- 409 version_mismatch: well-formed request with an unsupported integer protocol version.
- 404 unknown_goal: unsupported goal. Other unsupported routes/methods use http_error.
- Errors follow error-response.schema.json; arbitrary input is not echoed.

session_id is an opaque random token per login/demo session, never an account identifier.
revision is a nonnegative integer, increasing for each snapshot within that session.
The server echoes both without retaining state. The Java caller verifies the echoed
identity, version and goal. Future UI code must additionally reject results belonging to
an older revision/session than its current state; this console has one request per run.

Defence accepts integers 1..99 or null; omitted/null means unknown. Quest state accepts
not_started, in_progress, completed or unknown; omission means unknown. The account object
is mandatory but may be empty. Unknown is never interpreted as unmet or completed.
Unknown fields are rejected to catch misspelled observations. Additive wire changes must
be coordinated with this strict v1 model; breaking changes require a new protocol version.

The only goal is wear_rune_platebody: Defence >=40 and Dragon Slayer I completed. Source:
https://oldschool.runescape.wiki/w/Rune_platebody (requirement scope only).
Ownership, actual equipment, quest entry requirements, preparation, membership preferences
and recommendations are outside this slice. There is deliberately no overall 'ready' flag.

JSON schemas are exported from the Python wire models. Shared request/response fixtures
cover all-missing, all-satisfied and all-unknown cases; tests detect schema/fixture drift.

## Verification

```powershell
.\.venv\Scripts\python -m unittest discover -s tests -v
.\.venv\Scripts\python scripts/verify_bridge.py
```

The end-to-end check compiles Java, starts an isolated Python server on an ephemeral
loopback port, runs all three fixtures through Java, checks error propagation and stops
the server. It requires the installed API extras, JDK and downloaded Gson.

The service is local and stateless. Keep it bound to 127.0.0.1. This milestone does not
start background services or change the RuneLite installation.

## Three-goal browser

The original /v1/evaluate bridge contract remains unchanged. The sidebar now uses:

- GET /v1/catalog: descriptions of the three curated goals, benefits, investment,
  preparation, sources and review date (catalog-response.schema.json).
- POST /v1/goals/evaluate: goals-request.schema.json -> goals-response.schema.json.
  Returns all three goal descriptions, completion state, milestone counts and recursive
  requirement status rows in one snapshot. Session/revision semantics remain the same.

The extended account includes membership, quest points, Defence/Thieving levels, relevant
quest states, ten individually named Ardougne easy task flags and explicit armour-equipped
and cloak-claimed flags. Missing flags are unknown. Booleans and integers are strict;
unknown task keys are rejected. Goal IDs come from the catalogue, not from Java logic.

Requirements display met/missing/unknown separately from completed/incomplete/unknown
goal outcomes. All ten diary tasks must be confirmed for the diary group to be met; one
known unfinished task makes it missing, otherwise missing observations make it unknown.
Completion requires the explicit final milestone, not merely prerequisites. Membership
is an access check and quest-point entry context is informational; neither inflates the
top-level milestone count. Counts are not time/effort estimates. Base Thieving checks do
not infer unobserved temporary boosts. Preparation and combat readiness remain advice.

Synthetic goals-fresh/partial/complete/unclaimed/unknown.request.json fixtures demonstrate
the five states. Matching response fixtures are shared with Java tests. Example:

```powershell
.\bridge-java\run.ps1 -Fixture contracts/fixtures/goals-partial.request.json
```

Catalogue/rules: beginner_companion/domain/goals.json. API models: beginner_companion/api/goals.py.
The data is included as Python package data so installed services do not depend on the
working directory. See plugin/README.md for the browser/detail UI and launch commands.

## Preferences and recommendations

/v1/goals/evaluate accepts an optional preferences object, for example:

```json
{"interests": ["combat"], "commitment": "long"}
```

Allowed interests: combat, quests, exploration, utility. Choose 0..4 unique entries.
Commitment: any (default), short, few_sessions, long. Omission or an empty object means
no selections; invalid/duplicate values are rejected with 422. The account snapshot remains
independent of preferences, and the legacy /v1/evaluate endpoint is unchanged.

The browser response includes recommended_goal_id (nullable), recommendation_summary,
and each goal's recommendation: rank, recommended flag, availability, reasons and
considerations. The goals array is in recommendation order. Java renders Python's output
and performs no ranking. No raw score or match percentage is shown to the player.

Rule version is reflected in dataset beginner-goals-2. Goal matching metadata is curated
in goals.json. Python awards 6 points per shared interest and 3 for an exact commitment
band, otherwise subtracting the distance between bands. Ties retain catalogue order.
Completion/membership tiers take precedence: unfinished available goals first, membership
unknown next, membership-required future options next, completed goals last. 'Available'
means no known membership restriction; it does not claim combat/preparation readiness.

A single best match needs selected preferences, a positive match, and no membership block
or unknown membership requirement. Unknown completion is explicitly qualified. With no
answers, no available positive match, or all goals complete, the summary explains why
there is no personalised best match. All three goals remain visible.

Commitment matching uses the derived remaining-work band, not the fresh-account band.
Unknown progress gets no commitment bonus or penalty; milestone counts are never converted
to time estimates. Mismatched commitment and membership limitations
appear in considerations alongside positive reasons. No player behaviour is inferred.
The server is stateless; the sidebar keeps editable answers in memory for its session.

Shared preferences-combat/exploration/short.request.json and matching response fixtures
exercise distinct recommendations through both Python tests and the real Java caller.

```powershell
.\bridge-java\run.ps1 -Fixture contracts/fixtures/preferences-combat.request.json
```

Saved goals and the pinned focus are Java-owned local UI choices. They are deliberately
not included in these HTTP requests or Python rankings. The development client persists
them per demo account in data/private/companion-demo-goals.json; see plugin/README.md.

## Live observations

The goals request accepts source=live (default remains demo), all 24 optional skill-level
fields, and skill_xp keyed by those skill IDs (strict integers, 0..200000000). Live requests
use the same evaluation/recommendation logic. The response note identifies live observations
instead of calling them fictional. Invalid/missing observations remain unknown.
Only the seven quests relevant to the current catalogue are transmitted. Character names,
account hashes, local storage keys and authentication data are not protocol fields.

The Java plugin reads on RuneLite's client thread, refreshes from game events and coalesces
pending changes. Session/revision checks reject results from earlier accounts or modes.
Saved choices stay local under separate demo/live character keys; they do not affect the
Python ranking. Diary history and unobserved equipment milestones are intentionally omitted.

## Remaining investment

Each evaluated goal includes `remaining_investment`: a `state`, short `summary`, explanatory
`details` and a `caveat`. This is derived from current observations, not the recommendation
score or a fraction of milestones. It does not change goal completion; its commitment band
now influences recommendation order.

States distinguish completed goals, access restrictions, unknown progress, training, quests,
diary work, combined prerequisites and final steps. Skill XP gaps are shown only when the
observed XP is consistent with the observed base level. Level-only observations are not
treated as exact XP. In-progress quests do not imply a known stage count or completion fraction.
Unknown diary tasks and unverified reward/equipment flags are not counted as unfinished work.

`GoalDescription.investment` remains the qualitative investment from a fresh account.
The derived guidance describes the current character's remaining tracked work, with no hours,
rates, price predictions or combat-readiness claim. Supplies and travel remain unmeasured.

`remaining_investment.commitment_band` is `short`, `few_sessions`, `long`, `unknown`,
`completed` or `blocked`, with `commitment_reason` explaining the policy. A confirmed final
tracked step fits `short`; an unverified final flag does not. For pure training in this
early-game catalogue, consistent remaining XP of at most 10% of target XP fits `short`;
at most 50% fits `few_sessions`; otherwise the full training band is retained. Missing or
conflicting XP prevents a reduced training fit. These thresholds are explainable heuristics,
not validated duration forecasts or a general XP-to-time model. Quest stages are never
inferred from `in_progress`. Access restrictions and completed-goal exclusions remain first.
