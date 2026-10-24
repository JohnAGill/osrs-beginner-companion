"""Describe remaining observations, never infer time, cost, readiness or an optimal route.

XP thresholds use the standard formula documented at
https://oldschool.runescape.wiki/w/Experience (OSRS Wiki contributors).
"""
from math import floor


def xp_for_level(level):
    return sum(floor(n + 300 * 2 ** (n / 7)) for n in range(1, level)) // 4


def commitment_fit(goal, result, account):
    """Qualitative policy for this early-game catalogue, not XP/hour or session forecasts.

    Pure training: <=10% of target XP remains -> short; <=50% -> at most few_sessions.
    Never convert milestone counts or quest stages to a remaining fraction.
    """
    state = result['remaining_investment']['state']
    rows = [r for r in result['requirements'] if r['status'] != 'satisfied']
    if state == 'completed':
        return 'completed', 'This goal is already complete.'
    if state == 'access_blocked':
        return 'blocked', 'Access is blocked; remaining commitment is not rated.'
    if any(r['status'] == 'unknown' for r in rows if r['category'] != 'context') or state == 'unknown':
        return 'unknown', 'Unverified progress prevents a reliable remaining-commitment fit.'
    if state == 'final_step':
        return 'short', 'Only the final tracked step remains. Supplies, item ownership and travel are not measured.'
    if state == 'training':
        rules = {r['id']: r for r in goal['requirements']}
        gaps = []
        for row in rows:
            rule = rules[row['id']]
            if rule['kind'] != 'level':
                continue
            skill = rule['path'].split('.')[-1]
            xp = account.get('skill_xp', {}).get(skill)
            if xp is None or not xp_for_level(row['observed']) <= xp < xp_for_level(row['observed'] + 1):
                return 'unknown', 'Current consistent XP is needed to rate the remaining training; base level alone is insufficient.'
            gaps.append((xp_for_level(row['required']) - xp) / xp_for_level(row['required']))
        if not gaps:
            return 'unknown', 'Remaining training cannot be rated from these observations.'
        fraction = max(gaps)
        if fraction <= .1:
            return 'short', 'At most 10% of the target training XP remains. This is a qualitative fit, not a duration promise.'
        if fraction <= .5:
            band = 'few_sessions'
            return band, 'At most half of the target training XP remains; the training project is reduced. Methods and supplies still affect effort.'
        return goal['matching']['commitment'], 'Most of the target training XP remains; the training project still represents a substantial part of this goal.'
    if state == 'quest' and goal['id'] == 'wear_rune_platebody':
        return 'few_sessions', 'Quest work remains, with the tracked entry and skill prerequisites met. Quest stages and combat preparation are not measured.'
    if state == 'quest':
        return goal['matching']['commitment'], 'Quest completion still remains. Being in progress does not establish how many stages are left.'
    if state == 'diary':
        return 'few_sessions', 'Diary work remains; task counts are not converted to duration, and travel and supplies remain unmeasured.'
    return goal['matching']['commitment'], 'Multiple kinds of work still remain, including tracked prerequisites. The goal has not reduced to a final activity.'


def remaining_investment(goal, result, account):
    caveat = 'Tracked work only, not a time or cost estimate or a combat-readiness assessment. Supplies, travel and preparation may add effort.'
    if result['status'] == 'completed':
        return dict(state='completed', summary='Goal completed', details=['No tracked work remains.'], caveat=caveat)
    rows = {row['id']: row for row in result['requirements']}
    rules = {rule['id']: rule for rule in goal['requirements']}
    details, kinds = [], set()
    uncertain = False
    eligibility = [r for r in result['requirements'] if r['category'] == 'eligibility' and r['status'] != 'satisfied']
    for row in result['requirements']:
        if row['status'] == 'satisfied':
            continue
        rule = rules[row['id']]
        kind = rule['kind']
        # Quest entry context ceases to be remaining work once the quest is complete.
        if row['category'] == 'context' and rows.get('dragon_slayer_i', {}).get('status') == 'satisfied':
            continue
        if kind == 'all':
            missing = sum(c['status'] == 'missing' for c in row['children'])
            unknown = sum(c['status'] == 'unknown' for c in row['children'])
            details.append(f'Diary tasks: {missing} recorded unfinished; {unknown} unverified. Unverified tasks may already be done.')
            if missing: kinds.add('diary')
            uncertain |= unknown > 0
        elif row['status'] == 'unknown':
            details.append(row['label'] + ': progress unverified; check in game before repeating work.')
            uncertain = True
        elif kind == 'level':
            skill = rule['path'].split('.')[-1]
            level, target = row['observed'], row['required']
            line = f'{skill.title()}: base level {level} toward {target}.'
            xp = account.get('skill_xp', {}).get(skill)
            if xp is not None:
                if xp_for_level(level) <= xp < xp_for_level(level + 1):
                    line += f' {xp_for_level(target) - xp:,} XP to the target level.'
                else:
                    line += ' XP and level observations disagree; XP remaining is not shown.'
                    uncertain = True
            else:
                line += ' Exact XP remaining is not available from level alone.'
            if goal['id'] == 'ardougne_cloak_1' and row['id'] == 'thieving_5':
                line += ' A suitable boost can also satisfy the diary skill requirement; this reading tracks base level only.'
            details.append(line)
            kinds.add('training')
        elif kind == 'quest':
            underway = row['observed'] == 'in_progress'
            details.append(row['label'] + (': in progress; remaining quest stages are not tracked.' if underway
                                          else ': not started; quest preparation and completion remain.'))
            kinds.add('quest_underway' if underway else 'quest')
        elif kind == 'threshold':
            details.append(f"Quest points: {row['observed']} / {row['required']} for quest entry. Choose quests in any order.")
            kinds.add('entry')
        elif row['category'] != 'eligibility':
            details.append(row['label'] + ': recorded unfinished.')
            kinds.add('final')
        else:
            details.append(row['label'] + ': required before these activities.')

    final_id = {'wear_rune_platebody': 'equip_platebody', 'ardougne_cloak_1': 'claim_cloak'}.get(goal['id'])
    prerequisites = [r for r in result['requirements'] if r['category'] != 'context' and r['id'] != final_id]
    if eligibility:
        state, summary = ('access_blocked', 'Membership needed') if any(r['status'] == 'missing' for r in eligibility) else ('unknown', 'Membership needs checking')
    elif final_id and all(r['status'] == 'satisfied' for r in prerequisites):
        final = rows[final_id]
        state = 'final_step' if final['status'] == 'missing' else 'unknown'
        summary = ('Requirements met; final step remains' if state == 'final_step'
                   else 'Requirements met; confirm the final step')
        if final_id == 'equip_platebody':
            details.append('Obtain the armour if needed, then equip it. Ownership and purchase cost are not tracked.')
        else:
            details.append('All easy tasks are recorded complete; check whether the reward still needs claiming from Two-pints.')
    elif 'training' in kinds and kinds & {'quest', 'quest_underway', 'entry'}:
        state, summary = 'prerequisites', 'Training and quest work remain'
    elif 'entry' in kinds:
        state, summary = 'prerequisites', 'Quest entry requirements remain'
    elif 'training' in kinds:
        state, summary = 'training', 'Training remains'
    elif 'quest' in kinds:
        state, summary = 'quest', 'Quest work remains'
    elif 'quest_underway' in kinds:
        state, summary = 'quest', 'Quest underway; stages remain'
    elif 'diary' in kinds:
        state, summary = 'diary', 'Diary tasks remain'
    elif uncertain:
        state, summary = 'unknown', 'Remaining work needs checking'
    else:
        state, summary = 'final_step', 'Final tracked step remains'
    if uncertain and state not in {'unknown', 'completed'}:
        summary += '; some progress unverified'
    return dict(state=state, summary=summary, details=details, caveat=caveat)
