import unittest
from beginner_companion.domain.catalog import load_catalog, evaluate_goals


class ExpandedCatalogTests(unittest.TestCase):
    def setUp(self):
        self.catalog = load_catalog()

    def evaluate(self, account):
        return {r['goal']['id']: r for r in evaluate_goals(self.catalog, account)}

    def test_every_level_goal_handles_below_exact_above_and_unknown(self):
        for goal in self.catalog['goals']:
            completion = goal['completion']
            if completion.get('kind') != 'level':
                continue
            skill = completion['path'].split('.')[1]
            target = completion['required']
            for level, status in [(target - 1, 'incomplete'), (target, 'completed'), (target + 1, 'completed')]:
                with self.subTest(goal=goal['id'], level=level):
                    row = self.evaluate({'skill_levels': {skill: level}})[goal['id']]
                    self.assertEqual(row['status'], status)
                    self.assertEqual(bool(row['next_steps']), status != 'completed')
                    self.assertEqual(row['progress']['satisfied'], int(status == 'completed'))
            unknown = self.evaluate({})[goal['id']]
            self.assertEqual(unknown['status'], 'unknown')
            self.assertTrue(all(step['kind'] == 'check' for step in unknown['next_steps']))

    def test_new_quests_track_continuation_and_membership(self):
        for quest in ['cooks_assistant', 'the_restless_ghost', 'rune_mysteries', 'plague_city',
                      'sheep_shearer', 'dorics_quest']:
            with self.subTest(quest=quest):
                account = {'members': True, 'quest_progress': {quest: 'in_progress'}}
                row = self.evaluate(account)[quest]
                self.assertEqual(row['status'], 'incomplete')
                self.assertTrue(row['next_steps'][0]['title'].startswith('Continue '))
                account['quest_progress'][quest] = 'completed'
                self.assertEqual(self.evaluate(account)[quest]['next_steps'], [])
        blocked = self.evaluate({'members': False, 'quest_progress': {'plague_city': 'not_started'}})['plague_city']
        self.assertEqual([s['id'] for s in blocked['next_steps']], ['membership'])

    def test_members_training_is_gated_and_f2p_options_remain_actionable(self):
        account = {'members': False, 'skill_levels': {'agility': 1, 'thieving': 1,
                   'woodcutting': 1, 'crafting': 1, 'smithing': 1, 'ranged': 1}}
        results = self.evaluate(account)
        for goal in ['agility_30', 'thieving_20']:
            self.assertEqual(results[goal]['remaining_investment']['state'], 'access_blocked')
            self.assertEqual([s['id'] for s in results[goal]['next_steps']], ['membership'])
        for goal in ['woodcutting_15', 'woodcutting_30', 'crafting_5', 'smithing_30', 'ranged_20', 'ranged_30']:
            self.assertEqual(results[goal]['next_steps'][0]['kind'], 'activity')
        account['members'] = True
        for goal in ['agility_30', 'thieving_20']:
            self.assertEqual(self.evaluate(account)[goal]['next_steps'][0]['kind'], 'activity')

    def test_introductory_quests_do_not_require_gathering_levels(self):
        account = {'members': False, 'skill_levels': {'mining': 1, 'crafting': 1},
                   'quest_progress': {'sheep_shearer': 'not_started', 'dorics_quest': 'not_started'}}
        for goal in ['sheep_shearer', 'dorics_quest']:
            row = self.evaluate(account)[goal]
            self.assertEqual([r['id'] for r in row['requirements']], [goal])
            self.assertEqual(row['remaining_investment']['state'], 'quest')
            self.assertEqual(row['next_steps'][0]['kind'], 'activity')

    def test_all_goals_have_content_and_fresh_accounts_have_options(self):
        self.assertEqual(len(self.catalog['goals']), 33)
        for goal in self.catalog['goals']:
            for key in ['benefits', 'investment', 'preparation', 'sources', 'reviewed', 'matching']:
                self.assertTrue(goal[key], (goal['id'], key))
        account = {'members': False, 'skill_levels': {skill: 1 for skill in ['attack','magic','prayer','fishing','cooking','mining']},
                   'quest_progress': {q: 'not_started' for q in ['cooks_assistant','the_restless_ghost','rune_mysteries','plague_city']}}
        for goal in ['attack_20','attack_40','magic_25','magic_37','prayer_43','fishing_30','cooking_25','mining_30',
                     'cooks_assistant','the_restless_ghost','rune_mysteries']:
            self.assertTrue(self.evaluate(account)[goal]['next_steps'], goal)
