# Licensing and attribution

## Original code

Original Java/Python implementation, tests, scripts and original documentation are
Copyright (c) 2026 JohnAGill, under the [MIT License](LICENSE). This grant does not
relicense third-party code, wiki-derived content or game trademarks.

## OSRS Wiki content

Goal descriptions and requirements were curated and paraphrased from the **OSRS Wiki
contributors**. Their source pages are linked in each goal's `sources` field in
[goals.json](beginner_companion/domain/goals.json), and displayed in goal details.
Page histories identify the contributors: append `?action=history` to a source page URL.
The `reviewed` field records when the project reviewed the content; it is not a pinned
revision or a claim that future game updates have been incorporated.

The wiki states its content is licensed under
[Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported](https://creativecommons.org/licenses/by-nc-sa/3.0/),
see its [licensing notice](https://oldschool.runescape.wiki/w/Template:CC-BY-NC-SA).
The licence's [full legal text](https://creativecommons.org/licenses/by-nc-sa/3.0/legalcode)
is available from Creative Commons.

To keep adapted content clearly scoped, this project distributes the curated catalogue
`beginner_companion/domain/goals.json` and generated `contracts/fixtures/*.response.json`
under **CC BY-NC-SA 3.0**, rather than MIT. Changes include condensed prose, structured
requirements, investment bands, preference tags and machine-readable progress examples.
These files are not official wiki exports and the wiki does not endorse the project.

Wiki-derived text displayed in screenshots and generated runtime responses retains these
terms. The original UI rendering code remains MIT. This split is intentional: do not
assume that the MIT code licence makes the bundled catalogue available for commercial use.

## Bundled Gradle wrapper

`plugin/gradlew`, `plugin/gradlew.bat` and `plugin/gradle/wrapper/gradle-wrapper.jar`
come from the Gradle wrapper via the RuneLite example-plugin scaffold. The wrapper script
copyright and Apache-2.0 headers are retained. The Gradle 8.10 distribution's
[licence](LICENSES/Gradle-LICENSE.txt) and [notice](LICENSES/Gradle-NOTICE.txt) are included.
Upstream: [Gradle 8.10](https://github.com/gradle/gradle/tree/v8.10.0).

## Runtime and development dependencies

These are resolved by Gradle or pip; their binaries are not committed (except the wrapper).
Their own notices apply when distributing builds.

| Component | Upstream licence / source |
| --- | --- |
| RuneLite APIs/client | [BSD 2-Clause](LICENSES/RuneLite-BSD-2-Clause.txt); individual source files may have additional notices. [Upstream](https://github.com/runelite/runelite) |
| Gson | [Apache-2.0](https://github.com/google/gson/blob/main/LICENSE) |
| FastAPI | [MIT](https://github.com/fastapi/fastapi/blob/master/LICENSE) |
| Pydantic | [MIT](https://github.com/pydantic/pydantic/blob/main/LICENSE) |
| Uvicorn | [BSD 3-Clause](https://github.com/encode/uvicorn/blob/main/LICENSE.md) |
| HTTPX | [BSD 3-Clause](https://github.com/encode/httpx/blob/master/LICENSE.md) |
| JUnit 4 | [EPL 1.0](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt) |

## Screenshots and game identity

`docs/images/*.png` are real Swing/RuneLite overlay renders generated from fictional
fixtures. They contain no captured gameplay, character names, avatars or login screens.
The screenshot generator uses RuneLite's look and feel; no RuneScape textures, sprites,
music, logo assets or game client binaries are included in this repository.

Old School RuneScape and RuneScape are trademarks of Jagex Ltd. This independent portfolio
project is not affiliated with or endorsed by Jagex, RuneLite or the OSRS Wiki. No trademark
rights are granted by this repository's software licence. It is a development prototype,
not an approved Plugin Hub release.
