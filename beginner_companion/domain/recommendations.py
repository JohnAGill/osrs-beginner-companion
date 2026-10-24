"""Explainable preference matching; no inferred playstyle or time estimates."""
from copy import deepcopy
import time

# Curated relationships describe optional follow-ups, not prerequisites or a route.
FOLLOW_UPS = {
    'attack_20': {'attack_40': 'Continue melee equipment progression towards rune weapons.'},
    'magic_25': {'magic_37': 'Build on your first teleport with another travel option.'},
    'cooks_assistant': {'cooking_25': 'Build on the Cooking introduction with a food milestone.'},
    'the_restless_ghost': {'prayer_43': 'Build on the Prayer introduction with a longer combat utility goal.'},
    'rune_mysteries': {'ardougne_cloak_1': 'That quest contributes a prerequisite for the diary project.'},
    'plague_city': {'ardougne_cloak_1': 'That quest contributes a prerequisite for the diary project.'},
    'prince_ali_rescue': {'wear_rune_platebody': 'Consider a longer questing ambition after a standalone quest.'},
    'fishing_30': {'cooking_25': 'Pair your gathering milestone with preparing food.'},
    'cooking_25': {'fishing_30': 'Consider gathering the fish you can now cook.'},
}

INTERESTS = {'combat': 'combat', 'quests': 'quests and stories',
             'exploration': 'exploration', 'utility': 'useful unlocks'}
COMMITMENT = {'short': 0, 'few_sessions': 1, 'long': 2}
COMMITMENT_LABELS = {'short': 'a short activity', 'few_sessions': 'a project over a few sessions',
                     'long': 'a longer ambition'}


def recommend(catalog, evaluated, preferences, account, completion_history=(), *, now=None):
    """Return a new ordered result; membership/completion take priority over fit.

    Six points per selected interest match; exact commitment +3, otherwise minus the
    distance between remaining-work bands. Unknown remaining work gets no commitment
    bonus or penalty. No milestone-count-to-time conversion is performed.
    A curated follow-up gets +3 within seven days or +1 within thirty, once per goal.
    Explicit interests gate follow-up boosts. Ties retain catalogue order. With neither
    preferences nor recent history there is no single best match.
    """
    rows = deepcopy(evaluated)
    metadata = {goal['id']: goal['matching'] for goal in catalog['goals']}
    selected = set(preferences.get('interests', []))
    commitment = preferences.get('commitment', 'any')
    now = int(time.time()) if now is None else now
    completed_ids = {row['goal']['id'] for row in rows if row['status'] == 'completed'}
    titles = {row['goal']['id']: row['goal']['title'] for row in rows}
    recent = {}
    for event in completion_history:
        source, timestamp = event['goal_id'], event['completed_at']
        if source in completed_ids and 0 <= now - timestamp <= 30 * 86400:
            recent[source] = max(recent.get(source, 0), timestamp)
    personalised = bool(selected) or commitment != 'any' or bool(recent)
    scored = []
    for index, row in enumerate(rows):
        goal = row['goal']
        match = metadata[goal['id']]
        matched = [key for key in INTERESTS if key in selected and key in match['interests']]
        reasons, considerations = [], []
        score = len(matched) * 6
        if matched:
            reasons.append('Matches your interest in ' + ' and '.join(INTERESTS[key] for key in matched) + '.')
        elif selected:
            considerations.append('Its main activities do not directly match your selected interests.')
        followups = []
        if not selected or matched:
            for source, timestamp in recent.items():
                reason = FOLLOW_UPS.get(source, {}).get(goal['id'])
                if reason:
                    bonus = 3 if now - timestamp <= 7 * 86400 else 1
                    followups.append((bonus, timestamp, source, reason))
        if followups:
            bonus, _, source, reason = max(followups)
            score += bonus  # One recency bonus, never a pile-up from related completions.
            reasons.append('An option after recently completing "' + titles[source] + '": ' + reason)
        if commitment != 'any':
            remaining = row['remaining_investment']
            band = remaining['commitment_band']
            if band in COMMITMENT:
                distance = COMMITMENT[band] - COMMITMENT[commitment]
                if distance == 0:
                    score += 3
                    reasons.append('Its remaining work fits your preference for ' + COMMITMENT_LABELS[commitment] + '. ' + remaining['commitment_reason'])
                else:
                    score -= abs(distance)
                    considerations.append('Its remaining work is a ' + ('larger' if distance > 0 else 'smaller')
                                          + ' commitment than you selected. ' + remaining['commitment_reason'])
                considerations.append('Remaining commitment is qualitative, not a promised number of sessions or hours.')
            elif band == 'unknown':
                considerations.append('Remaining commitment is not scored: ' + remaining['commitment_reason'])
            elif band == 'blocked':
                considerations.append(remaining['commitment_reason'])
        if personalised and not reasons:
            reasons.append('An alternative to explore outside your selected preferences.')

        if row['status'] == 'completed':
            availability, tier = 'completed', 3
            considerations.append('This character has already completed this goal.')
        elif goal['members_only'] and account.get('members') is False:
            availability, tier = 'members_required', 2
            considerations.append('Requires membership. Kept visible as a future option.')
        elif goal['members_only'] and account.get('members') is None:
            availability, tier = 'membership_unknown', 1
            considerations.append('Requires membership; membership status is unknown.')
        else:
            availability, tier = 'available', 0
        if row['status'] == 'unknown':
            considerations.append('Completion is unknown; check progress before choosing this goal.')
        row['recommendation'] = dict(rank=0, recommended=False, availability=availability,
                                     reasons=reasons, considerations=considerations)
        scored.append((tier, -score, index, row))

    scored.sort(key=lambda item: item[:3])
    recommended_id = None
    for rank, (tier, negative_score, _, row) in enumerate(scored, 1):
        row['recommendation']['rank'] = rank
        if rank == 1 and personalised and tier == 0 and negative_score < 0:
            row['recommendation']['recommended'] = True
            recommended_id = row['goal']['id']
    if all(row['status'] == 'completed' for row in rows):
        summary = 'All catalogue goals are completed on this character. No new goal to recommend.'
    elif not personalised:
        summary = 'A mix to explore. Answer the optional questions for a personalised suggestion.'
    elif recommended_id:
        title = next(row['goal']['title'] for _, _, _, row in scored if row['goal']['id'] == recommended_id)
        summary = 'Best fit among the current goals: ' + title + '. You can still choose any goal.'
    else:
        summary = 'No available unfinished goal directly matches these preferences or recent completions. Explore the alternatives below.'
    return dict(goals=[row for _, _, _, row in scored], recommended_goal_id=recommended_id,
                recommendation_summary=summary)
