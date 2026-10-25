# Publication privacy review

Reviewed for the portfolio publication on 2026-10-24.

## Scope and outcome

- Reviewed candidate source/documentation files and all commits reachable from the
  public branch, including commit author/committer metadata.
- Checked for private keys, common GitHub/AWS credential formats, personal email addresses,
  local Windows user paths, generated/private file paths and unexpected binary files.
- Inspected all four README screenshots visually and checked PNG metadata chunks.
- Verified that fixtures are fictional and screenshot generation uses temporary synthetic
  storage, rather than the developer's saved account state or a game session.
- Confirmed that local account state, credentials, logs, dependency caches, account exports
  and build outputs are excluded by `.gitignore` and absent from the published file tree.

The initial private commit used a personal email address and machine-derived author name.
The publication history uses the GitHub username and GitHub no-reply address instead.
The original repository is retained separately as a **private draft**; the public repository
is created afresh with only the cleaned history, rather than relying on a force-push to
remove old commit objects from the same repository.

No credential or private account-data findings remained in the configured checks.
Upstream copyright holders' published contact details are intentionally retained in
`LICENSES/`, as required by their attribution notices.

## Repeatable audit

From the repository root:

```powershell
.\.venv\Scripts\python scripts/audit_publication.py
```

The script scans the non-ignored candidate files and every unique Git blob reachable from
`HEAD`, plus author/committer identities. It reports file/rule names without printing
matched credentials. Run it again before subsequent publications.

This is a pattern-based audit plus manual review, not a guarantee that every possible
secret format or sensitive inference can be detected. It does not inspect or upload ignored
personal files. Unpublished reflogs/backups are outside the public repository's history.

## Runtime data boundaries

The plugin keeps saved goals, tasks and completion observations per local character key.
The key is a hash used for separation, not a guarantee of anonymisation. A character name
can be displayed locally; neither that name nor the account hash/key is sent to Python.
Requests do contain skill/quest observations, preferences and recent completion goal IDs
with observation timestamps. Recommendation requests also include saved per-goal feedback
kinds and timestamps, without character identity. The default development launch uses loopback. Hosted configuration now requires an HTTPS
origin and explicit opt-in, with a destination/payload disclosure and cancellation when disabled.
The hosting provider receives connection metadata including IP addresses. The proposed container
disables access logs and the application does not log request bodies; provider log retention
still needs verification before deployment. No public planner endpoint has been created. A verified artifact is stored privately in
AWS S3; account identifiers and deployment metadata remain in ignored private files.

Screenshots are real UI renders with fictional data. They include no game login, credentials,
real character name, account export, gameplay capture or personal desktop background.
