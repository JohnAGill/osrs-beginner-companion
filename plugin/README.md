# RuneLite beginner goal browser

Local development plugin, pinned to RuneLite 1.13.3. Uses the shared PlannerClient from
bridge-java/src. Supports live character observations and the existing synthetic fixtures.

## Run (two terminals, repository root)

Start Python:

```powershell
.\.venv\Scripts\python -m uvicorn beginner_companion.api.app:app --host 127.0.0.1 --port 8765
```

Start the development client (JDK 17 recommended):

```powershell
.\plugin\gradlew.bat -p plugin --gradle-user-home .gradle-local run --no-daemon
```

First launch downloads Gradle and RuneLite dependencies. This launches a development
RuneLite client, not a change to your installed RuneLite. The official example launcher
pattern is used: https://github.com/runelite/example-plugin

Open **Beginner Companion** using the gold G icon in the right toolbar. If absent, find
Beginner Companion in RuneLite's plugin settings and enable it. Choose a demo account,
then click **Check demo account**. No game login is needed. If Python or the development
client was already open before this update, restart both to load the new code.

## Live character mode

The development client now opens the companion in **Live character** mode. Log in through
RuneLite's Jagex Accounts panel and select Play Now. After two logged-in game ticks, the
companion automatically reads a snapshot and sends it to the local Python service. The
**Refresh live data** button retries manually; **Character data** shows the observations.
You can still select any demo account using the same dropdown.

Live observations include all 24 base skill levels and XP, quest points, membership days
(converted to membership status), and the five relevant quest states. Quest states use
RuneLite's Quest.getState on the game thread. Stat/var/equipment events mark the data dirty;
the next game tick collects it, with a ten-tick periodic fallback. Identical data is not
resent. Pending requests coalesce to the latest snapshot, and failed requests back off
before automatic retry. Preference changes refresh live suggestions automatically.

Names are displayed only in the local sidebar. Neither the character name, account hash,
credentials nor local saved-goal key is sent to Python. Live saved goals use a local
SHA-256-derived character key, separate from every demo key, in the existing private
selection file. The wire session is random and renewed on mode/account transitions.

Logout, world-hop/loading transitions and character changes clear visible live progress,
cancel stale HTTP results and disable live edits until a fresh character is available.
Returning to a character restores its saved goals without importing any demo choices.
No live skill/quest snapshot is persisted by this feature.

Ardougne task flags and cloak reward-claim history remain **unknown**, since their mappings
have not been verified. The rune platebody milestone becomes true if wearing item 1127 is
observed, and is remembered per character for this plugin session (including after taking
it off). Past equipment use is unknown until observed; that history is not persisted across
plugin restarts. No false 'not completed' result is inferred from an empty equipment slot.
Combat readiness and preparation advice are still not automatically verified.

## In-game focus overlay

Pin a goal in **Live character** mode. A compact **Current focus** overlay appears in the
game view, even with the sidebar closed. It shows the goal title, completion state,
milestone count and up to two outstanding checks (including numeric skill progress).
Counts represent checks, not effort or estimated completion time.

Hold **Alt** and drag the overlay to reposition it using RuneLite's standard controls.
Its position is managed by RuneLite. Disable it with **Show focus overlay** in the
Beginner Companion plugin settings. Unpinning hides it; choosing another focus updates it
after the local save is confirmed. Completed goals remain visible until you unpin them.

The overlay appears only for the current logged-in live character. Demo browsing never
puts fictional progress over real gameplay. Logout/loading, another character, disabling
the plugin, or no pinned goal hides it. When Python is updating/unavailable, only the pinned
title and a status message are shown; old counts are not presented as current progress.
It reads an immutable snapshot published from the sidebar's state, performs no HTTP or
Swing access while rendering, and independently checks the game's active character key.

Overlay tests produce build/focus-overlay-preview.png and cover rendering, configuration,
pin/unpin, logout/account isolation and unavailable-progress states.

- Fresh F2P account: no completed goals; membership requirement is visible on the cloak.
- Partial members account: Prince Ali Rescue done, other milestones partly completed.
- All goals completed: armour equipped, cloak claimed and Prince Ali Rescue completed.
- Unlocks ready, not claimed: requirements/tasks met, but armour not yet worn and cloak
  not yet claimed. These goals remain incomplete.
- Unknown progress: unobserved requirements and completion remain unknown.

The browser shows three goals: wear a rune platebody, unlock Ardougne cloak 1, and complete
Prince Ali Rescue. **View goal** opens benefits, investment, requirements/progress,
preparation notes and source references. **Back to goals** returns without a network call.
The cloak's **Show diary tasks** expands the ten easy tasks, including their statuses.
Milestone counts are checks rather than time/effort percentages; membership and the quest
point entry context are shown separately. Preparation advice is not automatically verified.
All descriptions and evaluation rules live in Python; Java only renders the result.
Stop Python and check again to see the failure state; restart it and click again to retry.
The service address is fixed to 127.0.0.1:8765 for this demo milestone. Changing the demo
clears old results; click Check demo account to load its browser. Refreshing an account
keeps the selected detail view where possible.

## Optional preference questions

Open **Your preferences (optional)** above the check button:

- Select any interests: combat, quests/stories, exploration, useful unlocks.
- Select any commitment, a short activity, a few sessions, or a longer ambition.
- Click **Check demo account** to refresh the suggestions.

Python orders the goals and supplies the recommendation summary, reasons and tradeoffs.
The best eligible unfinished match is labelled **Best fit for your answers**. Every goal
remains browsable, including future membership options and completed goals. Details show
the same explanation. These are broad full-journey categories, not remaining-time estimates.

**Show a mixture** clears both answers. With no answers the browser does not claim a
personalised best match. Answers last for the plugin session, survive demo-account changes,
and are not written to disk. Editing answers invalidates pending/old results; click the
check button again. Nothing automatically selects or starts a goal.

Try **Partial members account**, Combat, A longer ambition for the rune platebody suggestion;
Exploration and A few sessions for the cloak. On **Fresh F2P account**, Quests and stories /
Useful unlocks and A short activity suggests Prince Ali Rescue. On All goals completed,
Python explains there is no unfinished goal to recommend.

## Saved goals and current focus

After loading a demo account, use **Save goal** on a goal card or its details. Open
**Saved goals (N)** to see that account's list, or **Explore goals** to return to recommendations.

- **Pin as focus** saves the goal if necessary and makes it the single current focus.
- Pinning a different goal replaces the focus and keeps both goals saved.
- **Unpin focus** clears the focus while retaining the saved goal.
- **Remove saved goal** removes it and clears the focus if that goal was pinned.
- **Open focus** opens its detail view when progress has been loaded from Python.

The focus is shown above both views. Saving/pinning never changes Python's recommendation
ranking and does not start an activity. Completed or members-only goals can be saved/pinned
deliberately; completion does not automatically remove a choice or select a different focus.

Each of the five demo accounts has separate choices. They survive client/plugin restarts
in **data/private/companion-demo-goals.json**, ignored by Git. The Gradle run task sets the
absolute location using the companion.stateFile JVM property. Only goal IDs, cached titles
and the focused ID are stored; no account exports, credentials or progress snapshots.
Preference answers still last only for the current plugin session.

Saved titles and focus remain accessible with Python offline. Progress is shown only after
a fresh account check; account switches and preference edits clear old progress. Saved goals
missing from a future catalogue remain removable, with an unavailable message rather than
silently disappearing. Local choices can still be removed/unpinned while offline.

Local reads/writes use a dedicated serial background worker. A change is confirmed in the
UI only after the atomic file replacement succeeds; controls show **Saving your choices...**
until then. If a write fails, the last confirmed choices remain and an error explains retry.
If the file cannot be read or has an unsupported version, editing is disabled to avoid
overwriting it. Fix/restore the file, then use **Retry loading saved goals**.

## Build and verify

```powershell
.\plugin\gradlew.bat -p plugin --gradle-user-home .gradle-local test --no-daemon
.\.venv\Scripts\python scripts/verify_bridge.py
```

Tests use an isolated local HTTP server to exercise panel rendering, selection changes
during requests, failure/retry and shutdown. They produce build/panel-preview.png for
layout inspection (goal-browser-preview.png and goal-detail-preview.png). The standalone
bridge remains runnable with bridge-java/run.ps1; it also accepts goals-*.request.json.
The preference test additionally writes preferences-preview.png and checks answer submission,
explanations, stale-response rejection and resetting to a mixture.
Saved-goal tests verify atomic persistence, focus invariants, per-demo isolation, switching
accounts while a save completes, offline restoration and file-error recovery. They use
temporary files or an in-memory store, never the development client's real saved choices.
The saved view preview is build/saved-focus-preview.png.

All UI state changes happen on Swing's event dispatch thread. HTTP runs asynchronously
on a two-thread daemon executor with connect/request timeouts. One request is accepted
at a time; changing the selected demo cancels/invalidates the previous request. Completion
callbacks check the current revision and closed state before updating the panel. Disabling
the plugin cancels pending work, shuts down its executor and removes its toolbar entry.

Later milestones can add verified diary/reward mappings and refine the overlay presentation. Public Plugin Hub packaging
and a bundled Python process are outside this development build.
