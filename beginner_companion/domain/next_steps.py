"""Small, independent options; unknown observations never mean unfinished work."""


def suggest_steps(result):
    if result['status'] == 'completed':
        return []
    rows = {r['id']: r for r in result['requirements']}
    steps = []

    def add(key, title, reason, kind='activity'):
        steps.append(dict(id=key, title=title, reason=reason, kind=kind))

    def done(key):
        return rows[key]['status'] == 'satisfied'

    def option(key, title, reason):
        row = rows[key]
        if row['status'] == 'unknown':
            add(key, 'Check: ' + row['label'], 'Progress is unknown; check in game before repeating this activity.', 'check')
        elif not done(key):
            add(key, title, reason)

    goal = result['goal']['id']
    if goal == 'wear_rune_platebody':
        option('defence_40', 'Work towards 40 Defence',
               f"Recorded Defence: {rows['defence_40']['observed']}/40. Train when you feel like combat; Dragon Slayer I also rewards Defence XP.")
        if not done('dragon_slayer_i'):
            if rows['dragon_slayer_i']['status'] == 'unknown':
                option('dragon_slayer_i', '', '')
            elif rows['dragon_slayer_i']['observed'] == 'in_progress':
                add('dragon_slayer_i', 'Continue Dragon Slayer I', 'Check your quest journal for your current stage and review combat preparation.')
            elif done('quest_points_32'):
                add('dragon_slayer_i', 'Consider starting Dragon Slayer I', 'You meet the 32 quest point entry requirement. Review the combat preparation first; this does not assess combat readiness.')
            else:
                option('quest_points_32', 'Choose quests towards 32 quest points',
                       f"Recorded quest points: {rows['quest_points_32']['observed']}/32. Pick quests you enjoy; no fixed quest order is needed.")
        if done('defence_40') and done('dragon_slayer_i'):
            option('equip_platebody', 'Obtain and equip a rune platebody', 'The tracked equip requirements are met. Obtain the armour if needed, then wear it.')
    elif goal == 'prince_ali_rescue':
        state = rows['prince_ali_rescue']['observed']
        option('prince_ali_rescue', 'Continue Prince Ali Rescue' if state == 'in_progress' else 'Try Prince Ali Rescue',
               'Use your quest journal to choose your next activity.' if state == 'in_progress' else
               'No prior quests or minimum skill levels are required. Speak to Hassan in Al Kharid palace; review supplies and guard preparation first.')
    elif goal == 'chronicle':
        option('obtain_chronicle', 'Visit Diango for a Chronicle', rows['obtain_chronicle']['note'])
        if rows['obtain_chronicle']['status'] == 'unknown':
            steps[-1]['title'] = 'Check for a Chronicle before buying one'
            steps[-1]['reason'] = rows['obtain_chronicle']['note'] + ' Ownership is unverified; an empty inventory does not prove you lack one.'
    elif goal == 'ardougne_cloak_1':
        if not done('membership'):
            option('membership', 'Keep this as a future members goal', 'These activities require membership. You can keep the goal saved and choose a free-to-play focus for now.')
            return steps
        option('thieving_5', 'Work towards 5 Thieving', 'This prepares you for the cake stall diary task; a suitable boost can also satisfy the requirement.')
        option('plague_city', 'Continue Plague City' if rows['plague_city']['observed'] == 'in_progress' else 'Try Plague City', 'A quest option that contributes towards the diary prerequisites.')
        if done('plague_city'):
            option('biohazard', 'Continue Biohazard' if rows['biohazard']['observed'] == 'in_progress' else 'Try Biohazard', 'Plague City is complete; Biohazard prepares access to the Combat Training Camp.')
        option('rune_mysteries', 'Continue Rune Mysteries' if rows['rune_mysteries']['observed'] == 'in_progress' else 'Try Rune Mysteries', 'This prepares the essence mine diary task.')
        tasks = rows['easy_tasks']
        if tasks['status'] == 'unknown' or any(t['status'] == 'unknown' for t in tasks['children']):
            add('check_diary', 'Review your easy Ardougne Diary', 'Some task progress is unknown. Open the in-game diary to see what remains; the plugin cannot confirm these tasks yet.', 'check')
        else:
            prerequisites = {'cake': 'thieving_5', 'training_camp': 'biohazard', 'essence_mine': 'rune_mysteries'}
            for task in tasks['children']:
                if task['status'] != 'missing' or (task['id'] in prerequisites and not done(prerequisites[task['id']])):
                    continue
                add(task['id'], task['label'], task['note'] or 'This diary task is recorded as unfinished. Review supplies and travel before attempting it.')
                break
        if done('easy_tasks'):
            option('claim_cloak', 'Claim your cloak from Two-pints', 'All ten easy tasks are recorded complete. Visit Two-pints at the Flying Horse Inn for the reward.')
    else:
        if 'membership' in rows and not done('membership'):
            option('membership', 'Keep this as a future members goal',
                   'These activities require membership. Keep this saved and choose a free-to-play focus for now.')
            return steps
        for row in result['requirements']:
            if row['category'] != 'milestone' and not (goal == 'the_grand_tree' and row['id'] == 'agility_25'):
                continue
            title = row['label']
            if row['observed'] == 'in_progress':
                title = title.replace('Complete ', 'Continue ', 1)
            reason = row['note']
            if isinstance(row['observed'], int) and not isinstance(row['observed'], bool):
                reason = f"Recorded level: {row['observed']}/{row['required']}. " + reason
            option(row['id'], title, reason)
    return steps
