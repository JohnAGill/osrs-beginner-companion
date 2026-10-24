import copy
import unittest
from beginner_companion.domain.catalog import evaluate_goals, load_catalog
from beginner_companion.domain.recommendations import recommend
from test_companion_goals import demo


class RemainingCommitmentTests(unittest.TestCase):
    def evaluate(self, account):
        return {r['goal']['id']: r for r in evaluate_goals(load_catalog(), account)}

    def match(self, account, interests=('combat',), commitment='short', history=()):
        catalog = load_catalog()
        return recommend(catalog, evaluate_goals(catalog, account),
                         {'interests': list(interests), 'commitment': commitment}, account, history, now=1800000000)

    def test_long_training_goal_becomes_short_as_xp_gap_shrinks(self):
        account = demo('complete')['account']
        account['skill_levels']['attack'] = 1
        account['skill_xp'] = {'attack': 0}
        before = self.match(account)
        self.assertEqual(before['recommended_goal_id'], 'attack_20')
        self.assertEqual(self.evaluate(account)['attack_40']['remaining_investment']['commitment_band'], 'long')
        account['skill_levels']['attack'] = 39
        account['skill_xp']['attack'] = 37000
        after = self.match(account)
        self.assertEqual(after['recommended_goal_id'], 'attack_40')
        self.assertEqual(after['goals'][0]['remaining_investment']['commitment_band'], 'short')
        reasons = ' '.join(after['goals'][0]['recommendation']['reasons'])
        self.assertIn('remaining work fits', reasons)
        self.assertNotIn('overall journey', reasons)

    def test_xp_policy_boundaries_are_not_level_or_milestone_fractions(self):
        for xp, level, band in [(18612, 33, 'few_sessions'), (18611, 33, 'long'),
                                (33502, 38, 'short'), (33501, 38, 'few_sessions')]:
            with self.subTest(xp=xp):
                row = self.evaluate({'skill_levels': {'attack': level}, 'skill_xp': {'attack': xp}})['attack_40']
                self.assertEqual(row['remaining_investment']['commitment_band'], band)

    def test_unknown_xp_and_flags_never_get_short_commitment_bonus(self):
        for account in [{'skill_levels': {'attack': 39}}, {'skill_levels': {'attack': 39}, 'skill_xp': {'attack': 0}}]:
            row = next(g for g in self.match(account)['goals'] if g['goal']['id'] == 'attack_40')
            self.assertEqual(row['remaining_investment']['commitment_band'], 'unknown')
            self.assertIn('not scored', ' '.join(row['recommendation']['considerations']))
            self.assertFalse(any('fits your preference' in reason for reason in row['recommendation']['reasons']))
        account = demo('unclaimed')['account']
        self.assertEqual(self.evaluate(account)['wear_rune_platebody']['remaining_investment']['commitment_band'], 'short')
        account['milestones'].pop('rune_platebody_equipped')
        self.assertEqual(self.evaluate(account)['wear_rune_platebody']['remaining_investment']['commitment_band'], 'unknown')

    def test_quest_in_progress_is_not_assumed_nearly_finished(self):
        account = demo('fresh')['account']
        account['quest_progress']['dragon_slayer_i'] = 'in_progress'
        account['skill_levels']['defence'] = 40
        account['quest_points'] = 32
        rune = self.evaluate(account)['wear_rune_platebody']['remaining_investment']
        self.assertEqual(rune['commitment_band'], 'few_sessions')
        self.assertIn('stages', rune['commitment_reason'])

    def test_history_membership_completion_and_any_preference_still_apply(self):
        account = demo('unclaimed')['account']
        blocked = demo('fresh')['account']
        result = self.match(blocked, ('exploration',))
        self.assertNotEqual(result['recommended_goal_id'], 'ardougne_cloak_1')
        completed = self.match(demo('complete')['account'])
        self.assertIsNone(completed['recommended_goal_id'])
        before = copy.deepcopy(account)
        result = self.match(account, commitment='any')
        self.assertFalse(any('remaining work fits' in r for g in result['goals'] for r in g['recommendation']['reasons']))
        self.assertEqual(account, before)
        near = demo('complete')['account']
        near['skill_levels']['attack'] = 39
        near['skill_xp'] = {'attack': 37000}
        result = self.match(near, history=[{'goal_id':'attack_20','completed_at':1800000000}])
        self.assertTrue(any('recently completing' in r for r in result['goals'][0]['recommendation']['reasons']))
