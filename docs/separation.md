# Separation from the earlier planner

The parent workspace contains an earlier quest-route planner, training-method tools,
personal account exports and the companion prototype. A dedicated companion tree was
created from an explicit file allowlist, leaving all original files intact.

Included: companion API/domain modules, catalogue, schemas/fictional fixtures, RuneLite
plugin, shared Java bridge, relevant tests, Gradle wrapper, bridge scripts and companion docs.

Excluded: RFD/cape route engine, training-method tools/data, account importer, personal
exports, data/private contents, virtual environments, logs, game assets and dependency caches.

The Python package is now beginner_companion (domain and api), replacing the prototype's
quest_planner namespace. All imports, service launch commands, package-data declarations,
tests and end-to-end launch references have been updated. Java sources/build paths are
relative only to this project. No source file is loaded from the parent workspace.

This folder is its own Git repository, published as
JohnAGill/osrs-beginner-companion. It can be moved out of the parent workspace independently.
The parent .gitignore excludes it to prevent accidentally committing a nested project.
Work on the companion here from now on. The original tree remains a preserved prototype;
running it still launches the older source location.

The private saved-choice file was subsequently copied locally when enabling the focus
overlay, without overwriting destination choices. It remains in the ignored data/private
directory. No account exports, credentials or progress snapshots were migrated or committed.
