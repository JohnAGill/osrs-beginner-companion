# Third-party components

- RuneLite APIs and client: https://github.com/runelite/runelite (BSD 2-Clause;
  individual files may carry additional notices). Resolved as Gradle dependencies.
- Gradle wrapper: https://github.com/runelite/example-plugin and
  https://github.com/gradle/gradle (Apache License 2.0). Preserve wrapper script headers.
- Gson: https://github.com/google/gson (Apache License 2.0). Downloaded by setup.ps1
  or resolved by Gradle, not committed as a dependency JAR.
- FastAPI, Pydantic, Uvicorn and HTTPX: installed from the pinned Python package metadata;
  their own distributions provide license notices.
- Goal facts are sourced from OSRS Wiki pages listed in the catalogue. No RuneScape
  game assets, game client binaries or personal account exports are included here.

This document records provenance; it does not grant a license for the project's original
code. Select the appropriate project license before distributing it publicly. Retain
dependency license files when packaging binaries.
