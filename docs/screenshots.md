# Portfolio screenshots

The README images capture the real `CompanionPanel` Swing hierarchy and `FocusOverlay`
renderer using RuneLite's `RuneLiteLAF`. They are generated from the fictional partial
account/preference fixture, with a synthetic pinned goal and current task.

They are **UI renders, not screenshots of a logged-in game session**. The overlay uses a
minimal fake client solely for rendering and identity guards. No game process is started,
no game input is sent, and no personal state, account exports or login screens are read.

Regenerate from the repository root:

```powershell
.\plugin\gradlew.bat -p plugin --gradle-user-home .gradle-local portfolioScreenshots --no-daemon
```

The task starts/stops a fixture-only loopback HTTP server and uses temporary saved-goal
storage. Output is written directly to `docs/images/`, at 2× scale for legibility. Images
show the top of a narrow sidebar viewport; content below the viewport remains scrollable.
The isolated overlay image has a transparent background. No captured image is edited to
replace names, remove warnings or imply functionality that is not implemented.

`PortfolioScreenshots.java` is a development-only entry point in the test source set;
it is not part of the production plugin. Screenshot content/attribution is described in
[third-party notices](../THIRD_PARTY_NOTICES.md).
