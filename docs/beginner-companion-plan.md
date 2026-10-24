# Beginner Companion roadmap

Implemented: shared JSON contracts, Python HTTP service, standalone Java caller, fifteen-goal
catalogue, goal browser/detail views, preference matching with explanations, saved goals,
pinned focus, a movable in-game focus overlay, and live RuneLite observations with logout/account isolation.

Next milestones:

1. Playtest live behaviour with a fresh F2P character and refine the in-game focus presentation.
2. Verify diary-task/reward-claim mappings; preserve unknown observations until verified.
3. Playtest the preference and recent-completion follow-up recommendations; expand curated relationships and validate investment guidance. Completion history is persisted per character locally, with initial completed observations kept as a baseline.
4. Choose a delivery model (see delivery-options.md) and discuss Plugin Hub review constraints.

Boundaries: no imposed account-wide route, automatic gameplay, unverified time promises,
or inferred completion from missing data. Java owns observations/UI/local choices; Python
owns curated requirements and explained recommendations.

This is the standalone successor to the original companion prototype. See separation.md
for provenance and excluded material. Earlier RFD/cape planning code is not a dependency.
