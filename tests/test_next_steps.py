import unittest
from beginner_companion.domain.catalog import evaluate_goals, load_catalog
from tests.test_companion_goals import demo


class NextStepsTests(unittest.TestCase):
    def steps(self, goal, account):
        return next(r['next_steps'] for r in evaluate_goals(load_catalog(), account) if r['goal']['id'] == goal)

    def test_rune_options_change_with_progress(self):
        account = demo('fresh')['account']
        steps = self.steps('wear_rune_platebody', account)
        self.assertEqual([s['id'] for s in steps], ['defence_40', 'quest_points_32'])
        account['quest_points'] = 32
        self.assertIn('starting', self.steps('wear_rune_platebody', account)[1]['title'])
        account['quest_progress']['dragon_slayer_i'] = 'in_progress'
        self.assertIn('Continue', self.steps('wear_rune_platebody', account)[1]['title'])
        account['quest_progress']['dragon_slayer_i'] = 'completed'
        account['skill_levels']['defence'] = 40
        self.assertEqual([s['id'] for s in self.steps('wear_rune_platebody', account)], ['equip_platebody'])
        account['milestones']['rune_platebody_equipped'] = True
        self.assertEqual(self.steps('wear_rune_platebody', account), [])

    def test_membership_and_prerequisite_gates(self):
        account = demo('fresh')['account']
        self.assertEqual([s['id'] for s in self.steps('ardougne_cloak_1', account)], ['membership'])
        account['members'] = True
        self.assertNotIn('biohazard', [s['id'] for s in self.steps('ardougne_cloak_1', account)])
        account['quest_progress']['plague_city'] = 'completed'
        self.assertIn('biohazard', [s['id'] for s in self.steps('ardougne_cloak_1', account)])

    def test_unknown_diary_is_check_not_repeat_or_claim(self):
        account = demo('partial')['account']
        account['ardougne_easy_tasks'] = {}
        steps = self.steps('ardougne_cloak_1', account)
        self.assertEqual(next(s['kind'] for s in steps if s['id'] == 'check_diary'), 'check')
        self.assertNotIn('claim_cloak', [s['id'] for s in steps])
        for s in self.steps('wear_rune_platebody', {}):
            self.assertEqual(s['kind'], 'check')

    def test_claim_and_quest_continuation(self):
        account = demo('unclaimed')['account']
        self.assertIn('claim_cloak', [s['id'] for s in self.steps('ardougne_cloak_1', account)])
        account['quest_progress']['prince_ali_rescue'] = 'in_progress'
        self.assertEqual(self.steps('prince_ali_rescue', account)[0]['title'], 'Continue Prince Ali Rescue')
        account['quest_progress']['prince_ali_rescue'] = 'completed'
        self.assertEqual(self.steps('prince_ali_rescue', account), [])
