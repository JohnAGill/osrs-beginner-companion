import copy
import unittest
from beginner_companion.domain.catalog import evaluate_goals, load_catalog
from beginner_companion.domain.investment import xp_for_level
from test_companion_goals import demo


class RemainingInvestmentTests(unittest.TestCase):
    def remaining(self, goal, account):
        return next(r['remaining_investment'] for r in evaluate_goals(load_catalog(), account) if r['goal']['id'] == goal)

    def test_xp_thresholds_and_partial_level_progress(self):
        self.assertEqual([xp_for_level(n) for n in [1, 2, 25, 40, 43, 99]], [0, 83, 7842, 37224, 50339, 13034431])
        account = {'skill_levels': {'attack': 39}, 'skill_xp': {'attack': 37000}}
        result = self.remaining('attack_40', account)
        self.assertEqual(result['state'], 'training')
        self.assertIn('224 XP', ' '.join(result['details']))
        account['skill_xp']['attack'] = 37223
        self.assertIn('1 XP', ' '.join(self.remaining('attack_40', account)['details']))
        account['skill_levels']['attack'] = 40
        account['skill_xp']['attack'] = 37224
        self.assertEqual(self.remaining('attack_40', account)['state'], 'completed')

    def test_missing_or_conflicting_xp_never_invents_a_gap(self):
        account = {'skill_levels': {'attack': 39}}
        self.assertIn('not available from level alone', ' '.join(self.remaining('attack_40', account)['details']))
        account['skill_xp'] = {'attack': 0}
        result = self.remaining('attack_40', account)
        self.assertIn('disagree', ' '.join(result['details']))
        self.assertNotIn('37,224 XP', ' '.join(result['details']))
        unknown = self.remaining('attack_40', {'skill_xp': {'attack': 37000}})
        self.assertEqual(unknown['state'], 'unknown')

    def test_quest_underway_is_not_a_completion_fraction(self):
        account = {'quest_progress': {'prince_ali_rescue': 'in_progress'}}
        result = self.remaining('prince_ali_rescue', account)
        self.assertIn('underway', result['summary'])
        self.assertIn('stages are not tracked', ' '.join(result['details']))
        self.assertNotIn('%', str(result))

    def test_rune_prerequisites_and_final_step_differ(self):
        fresh = self.remaining('wear_rune_platebody', demo('fresh')['account'])
        self.assertEqual(fresh['state'], 'prerequisites')
        self.assertIn('Quest points: 0 / 32', ' '.join(fresh['details']))
        account = demo('unclaimed')['account']
        final = self.remaining('wear_rune_platebody', account)
        self.assertEqual(final['state'], 'final_step')
        self.assertIn('Ownership and purchase cost are not tracked', ' '.join(final['details']))
        account['milestones'].pop('rune_platebody_equipped')
        self.assertEqual(self.remaining('wear_rune_platebody', account)['summary'], 'Requirements met; confirm the final step')
        account['quest_points'] = None
        self.assertFalse(any('quest entry' in line.lower() for line in self.remaining('wear_rune_platebody', account)['details']))

    def test_access_unknown_diary_and_reward_claim(self):
        self.assertEqual(self.remaining('ardougne_cloak_1', demo('fresh')['account'])['state'], 'access_blocked')
        account = demo('partial')['account']
        # A mixture of confirmed unfinished and unverified tasks is not all unfinished.
        account['ardougne_easy_tasks'] = {'cake': False, 'altar': True}
        result = self.remaining('ardougne_cloak_1', account)
        self.assertIn('1 recorded unfinished; 8 unverified', ' '.join(result['details']))
        self.assertIn('unverified', result['summary'])
        final = self.remaining('ardougne_cloak_1', demo('unclaimed')['account'])
        self.assertEqual(final['state'], 'final_step')
        self.assertIn('Two-pints', ' '.join(final['details']))

    def test_all_goals_complete_or_unknown_and_evaluation_does_not_mutate(self):
        account = demo('complete')['account']
        before = copy.deepcopy(account)
        for row in evaluate_goals(load_catalog(), account):
            self.assertEqual(row['remaining_investment']['state'], 'completed')
        self.assertEqual(account, before)
        for row in evaluate_goals(load_catalog(), {}):
            self.assertEqual(row['remaining_investment']['state'], 'unknown')
