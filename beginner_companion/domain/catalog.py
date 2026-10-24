"""Load and evaluate the small curated catalogue without HTTP or game dependencies."""
import json
from .next_steps import suggest_steps
from .investment import remaining_investment, commitment_fit
from importlib.resources import files


def load_catalog():
    catalog = json.loads(files(__package__).joinpath('goals.json').read_text(encoding='utf-8'))
    ids = [goal['id'] for goal in catalog['goals']]
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate goal IDs')
    for goal in catalog['goals']:
        matching = goal.get('matching', {})
        if (not matching.get('interests') or
                not set(matching['interests']) <= {'combat', 'quests', 'exploration', 'utility'} or
                matching.get('commitment') not in {'short', 'few_sessions', 'long'}):
            raise ValueError('Invalid goal matching metadata')
        seen = set()
        def validate(rule):
            if rule['id'] in seen:
                raise ValueError('Duplicate requirement ID')
            seen.add(rule['id'])
            if rule['kind'] == 'all':
                if not rule['children']:
                    raise ValueError('Empty requirement group')
                for child in rule['children']:
                    validate(child)
            elif rule['kind'] not in ('level', 'quest', 'flag', 'threshold'):
                raise ValueError('Unsupported requirement kind')
        for rule in goal['requirements']:
            validate(rule)
    return catalog


def observation(account, path):
    value = account
    for key in path.split('.'):
        if not isinstance(value, dict):
            return None
        value = value.get(key)
    return value


def evaluate_rule(rule, account):
    children = [evaluate_rule(child, account) for child in rule.get('children', [])]
    value = observation(account, rule.get('path', ''))
    if rule['kind'] == 'all':
        states = [child['status'] for child in children]
        status = 'missing' if 'missing' in states else 'unknown' if 'unknown' in states else 'satisfied'
        value = f"{states.count('satisfied')} / {len(states)} tasks done"
    elif value is None or value == 'unknown':
        status = 'unknown'
    elif rule['kind'] in ('level', 'threshold'):
        status = 'satisfied' if value >= rule['required'] else 'missing'
    else:
        status = 'satisfied' if value == rule['required'] else 'missing'
    return dict(id=rule['id'], label=rule['label'], status=status, observed=value,
                required=rule.get('required', 'all tasks'), note=rule.get('note', ''),
                category=rule.get('category', 'milestone'), children=children)


def evaluate_goals(catalog, account):
    results = []
    for goal in catalog['goals']:
        requirements = [evaluate_rule(rule, account) for rule in goal['requirements']]
        milestones = [row for row in requirements if row['category'] == 'milestone']
        outcome = observation(account, goal['completion']['path'])
        completed = (outcome is not None and outcome != 'unknown' and outcome >= goal['completion']['required']
                     if goal['completion'].get('kind') == 'level'
                     else outcome == goal['completion']['required'])
        status = 'completed' if completed else 'unknown' if outcome is None or outcome == 'unknown' else 'incomplete'
        results.append(dict(
            goal={key: value for key, value in goal.items() if key not in ('requirements', 'completion', 'matching')},
            status=status, requirements=requirements,
            progress={
                'satisfied': sum(row['status'] == 'satisfied' for row in milestones),
                'missing': sum(row['status'] == 'missing' for row in milestones),
                'unknown': sum(row['status'] == 'unknown' for row in milestones),
                'total': len(milestones),
            },
        ))
    for goal, result in zip(catalog['goals'], results):
        result['next_steps'] = suggest_steps(result)
        result['remaining_investment'] = remaining_investment(goal, result, account)
        band, reason = commitment_fit(goal, result, account)
        result['remaining_investment'].update(commitment_band=band, commitment_reason=reason)
    return results
