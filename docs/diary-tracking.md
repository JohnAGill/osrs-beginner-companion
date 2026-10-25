# Live Ardougne easy diary tracking

Reviewed 2026-10-25 against the RuneLite 1.13.3 API used by this project.
This is source verification and automated validation, not a claim of live playtesting
every task. Only the easy Ardougne diary is covered.

`LiveSnapshotReader` reads varp 1196 (`ARDOUNGE_ACHIEVEMENT_DIARY`, upstream spelling).
The bits are zero-based and are **not contiguous**:

| Contract task ID | Bit |
| --- | ---: |
| essence_mine | 0 |
| cake | 1 |
| silk | 2 |
| altar | 4 |
| trawler | 5 |
| training_camp | 6 |
| rusty_sword | 7 |
| wilderness_lever | 9 |
| hunter_shop | 11 |
| pet_check | 12 |

Task positions were checked against the upstream author's implementation in
[Quest Helper ArdougneEasy.java, commit 06da90f](https://github.com/Zoinkwiz/quest-helper/blob/06da90f982518a093f2fe1c9cfac5547fa7053d3/src/main/java/com/questhelper/helpers/achievementdiaries/ardougne/ArdougneEasy.java).
The implementation here is independently written; no Quest Helper code is bundled.

RuneLite's pinned API provides [VarPlayerID](https://github.com/runelite/runelite/blob/9821e4d4e8f46188eae276eed29babb65d66b929/runelite-api/src/main/java/net/runelite/api/gameval/VarPlayerID.java)
and [VarbitID](https://github.com/runelite/runelite/blob/9821e4d4e8f46188eae276eed29babb65d66b929/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java):

- 6291: `ARDOUGNE_EASY_COUNT`, expected 0–10.
- 4458: `ARDOUGNE_DIARY_EASY_COMPLETE`, expected 0 or 1.
- 4499: `ARDOUGNE_EASY_REWARD`, expected 0 or 1; 1 means claimed.

The separate count, total of ten and reward fields are corroborated by RuneStar's
game-script dump at [diary_completion_info](https://github.com/RuneStar/cs2-scripts/blob/7da6c1fbab51b7528ff13b15a532a44208bf75d6/scripts/%5Bproc%2Cdiary_completion_info%5D.cs2).
[update_diary_entry](https://github.com/RuneStar/cs2-scripts/blob/7da6c1fbab51b7528ff13b15a532a44208bf75d6/scripts/%5Bproc%2Cupdate_diary_entry%5D.cs2)
uses the reward value of 1 to display a claimed tier.
No game scripts are distributed in this repository.

The reader checks the number of set task bits against the count and checks that
the completion flag matches all ten tasks. A claimed reward must also have a
completed diary. Inconsistent values, invalid reward values or unavailable reads
leave both diary observations unknown until a subsequent snapshot. Other tiers'
bits are ignored. Finishing ten tasks does not imply the cloak was claimed.

These observations flow through the existing Python progress, next-step,
remaining-investment, recommendation and completion-history logic. They are read
again with live snapshots, and never cached across characters. No extra identity
data is sent or snapshots persisted.

Automated reader tests cover every individual bit, unrelated bits, an unclaimed
completed diary, a claimed reward, character switching and unavailable/inconsistent
reads. Existing contract tests cover partial, complete, unclaimed and unknown
diary progress. For live validation, compare the expanded task list with the game
journal, complete a task and confirm the count changes, then claim from Two-pints
and confirm the goal completes independently of cloak inventory/equipment.
