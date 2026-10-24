# OSRS Beginner Companion

A RuneLite development plugin that helps new Old School RuneScape players choose goals,
understand their requirements, and track their progress. Java reads game events and renders
the sidebar; a local Python service evaluates prerequisites and explains recommendations.

This is a standalone project. It does not depend on the earlier quest-route planner.

## Features

- Fifteen curated goals spanning combat, quests, travel and gathering, with live progress and optional next steps. Explore supports title/summary search and free-to-play/member filters.
- Optional interest and commitment questions with deterministic, explained recommendations.
- Per-character completion history, saved locally. Newly observed completions influence explained follow-up suggestions alongside preferences; an expandable history lives in Focus.
- Goal details with benefits, investment, requirement checks, preparation and sources.
- Saved goals and one pinned focus, persisted locally and isolated by character/demo account.
- Movable in-game focus overlay with milestone progress and outstanding checks.
- Live sidebar refreshes retain the last confirmed view, scroll position, and expanded diary tasks while progress updates arrive.
- The sidebar keeps setup in **Settings**, uses compact goal summaries, and expands explanations, preparation and sources on demand. Selecting a task tucks alternatives under **Choose another task**; pinned-goal suggestions are shown once rather than repeated in its detail view.
- **Focus** is the default sidebar tab for the pinned goal and current task. **Explore** and **Saved** have their own goal lists and detail views, without the focus section repeated above them. Switching tabs keeps your saved goal and task choices.
- Choose **Work on this** on a pinned goal's suggestion to save a current task per character. The overlay keeps the larger goal and shows that task instead of other options. Completed tasks stay marked completed until you clear or replace them; unavailable suggestions prompt review. Switching or unpinning the goal clears its task. Older saved-goal files remain readable.
- Python suggests independent next steps for each goal from observed progress. The pinned focus and goal detail explain each option; the in-game overlay shows up to two compact suggestions. Quest entry and membership prerequisites gate suggestions, completed checks disappear, and unknown diary progress prompts an in-game check. Suggestions are choices, not a fixed route or an assessment of combat readiness.
- Live skill levels/XP, relevant quest progress, quest points and membership observations.
- Five fictional demo accounts, so reviewers can explore without an OSRS login.
- Asynchronous HTTP, timeout handling, snapshot coalescing and stale-response rejection.

Unknown observations remain unknown. Diary task/reward-claim mappings are not yet verified.
Rune platebody use is remembered only when observed during the plugin session. No automatic
gameplay, movement or input is performed. This is a development build, not a published or
approved Plugin Hub release.

## Quick start (Windows / PowerShell)

Requirements: Python 3.10+, JDK 17 with java/javac on PATH. Run commands from this folder.

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[api,api-test]"
.\.venv\Scripts\python -m uvicorn beginner_companion.api.app:app --host 127.0.0.1 --port 8765
```

In a second terminal:

```powershell
.\plugin\gradlew.bat -p plugin --gradle-user-home .gradle-local run --no-daemon
```

Open the gold G sidebar. In **Settings**, select a fictional demo and click **Check demo account**, or log
into RuneLite yourself and use **Live character** mode. Authentication is handled by
RuneLite; the companion never needs your password or login tokens.

For a console-only Java/Python demo:

```powershell
.\bridge-java\setup.ps1
.\bridge-java\run.ps1 -Fixture contracts/fixtures/preferences-combat.request.json
```

## Project structure

```text
beginner_companion/
  api/             FastAPI adapter and strict wire models
  domain/          Goal catalogue, progress evaluation and preference matching
plugin/            RuneLite sidebar, live event reader, local saved-goal storage
bridge-java/       Shared asynchronous HTTP client and standalone console caller
contracts/         JSON schemas and fictional request/response fixtures
tests/             Python API/domain correctness tests
scripts/           End-to-end Java -> Python -> Java verification
docs/              Architecture, delivery options and separation notes
```

The rules remain in Python. Java supplies observations and choices; it does not duplicate
recommendation logic. Goal matching is transparent rules-based scoring, not machine learning.

## Goal catalogue

| Area | Goals |
| --- | --- |
| Combat and equipment | Wear a rune platebody; reach 20 Attack for mithril weapons; reach 40 Attack for rune weapons; reach 43 Prayer for Protect from Melee |
| Quests | Prince Ali Rescue; Cook's Assistant; The Restless Ghost; Rune Mysteries; Plague City (members) |
| Travel | Ardougne cloak 1 (members); 25 Magic for Varrock Teleport; 37 Magic for Falador Teleport |
| Gathering and food | 30 Fishing for salmon; 25 Cooking for salmon; 30 Mining for coal |

Skill goals complete at or above the base level, independently of owning equipment or
performing the unlocked activity. Seven relevant quests are read live. Each catalogue
entry includes sources, review date, investment guidance and preference-matching metadata.
The matcher considers preferences, membership, observed completion and recent completion
history. The first observation of an already-completed goal is a baseline, not a recent
event. A confirmed incomplete-to-completed transition is recorded once, including across
restarts; unknown observations neither create completions nor erase earlier observations.
Dates are when completion was observed, not necessarily when it happened in game.

Curated follow-ups receive one small boost: three points for observations in the last seven
days, one point up to thirty days, then none. Explicit interests gate that boost; membership
and completed-goal exclusions still take precedence. Unknown IDs, future timestamps and
events not supported by the current completed status do not influence ranking. Only goal
IDs and observation timestamps (up to fifty recent entries) are sent with evaluation
requests; character identity remains local. No goal or task is selected automatically.

## Verification

```powershell
.\.venv\Scripts\python -m unittest discover -s tests -v
.\plugin\gradlew.bat -p plugin --gradle-user-home .gradle-local test --no-daemon
.\.venv\Scripts\python scripts/verify_bridge.py
```

Run bridge-java/setup.ps1 before the last command on a fresh checkout. Java tests use
synthetic fixtures and temporary storage. The end-to-end script starts and stops its own
Python service on an ephemeral loopback port.

## Local data and Git

Only synthetic fixtures are included. Saved choices are written to
data/private/companion-demo-goals.json and ignored by Git. The filename is historical;
live characters have separate hashed local keys. Credentials, account exports, logs,
dependency caches and build output are not part of the source repository.

Source repository: [JohnAGill/osrs-beginner-companion](https://github.com/JohnAGill/osrs-beginner-companion).
Choose a license for your own code before public distribution; third-party code retains
its own terms. See [third-party notices](THIRD_PARTY_NOTICES.md).

More detail: [plugin usage](plugin/README.md), [message contract](contracts/README.md),
[delivery options](docs/delivery-options.md), [separation notes](docs/separation.md).
