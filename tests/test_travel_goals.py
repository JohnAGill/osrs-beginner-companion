import unittest
from beginner_companion.domain.catalog import evaluate_goals, load_catalog
from beginner_companion.domain.recommendations import recommend


class TravelGoalTests(unittest.TestCase):
    def evaluate(self, account):
        return {g['goal']['id']: g for g in evaluate_goals(load_catalog(), account)}

    def test_chronicle_ownership_does_not_claim_charges_or_teleport_use(self):
        unknown = self.evaluate({})['chronicle']
        self.assertEqual(unknown['status'], 'unknown')
        self.assertEqual(unknown['next_steps'][0]['kind'], 'check')
        self.assertIn('bank', unknown['next_steps'][0]['reason'])
        missing = self.evaluate({'milestones': {'chronicle_obtained': False}})['chronicle']
        self.assertEqual(missing['status'], 'incomplete')
        self.assertEqual(missing['remaining_investment']['commitment_band'], 'short')
        self.assertEqual(missing['next_steps'][0]['kind'], 'activity')
        complete = self.evaluate({'milestones': {'chronicle_obtained': True}})['chronicle']
        self.assertEqual(complete['status'], 'completed')
        self.assertEqual(complete['next_steps'], [])
        self.assertTrue(any('Charges' in note for note in complete['goal']['preparation']))

    def test_glider_quest_can_start_before_agility_or_spirit_tree_quest(self):
        account = {'members': True, 'skill_levels': {'agility': 1},
                   'quest_progress': {'the_grand_tree': 'not_started', 'tree_gnome_village': 'not_started'}}
        row = self.evaluate(account)['the_grand_tree']
        self.assertEqual({s['id'] for s in row['next_steps']}, {'agility_25', 'the_grand_tree'})
        self.assertEqual(next(r for r in row['requirements'] if r['id'] == 'agility_25')['category'], 'context')
        self.assertEqual(row['remaining_investment']['summary'], 'Agility preparation and quest work remain')
        self.assertTrue(any('boost' in d for d in row['remaining_investment']['details']))
        account['quest_progress']['the_grand_tree'] = 'in_progress'
        self.assertTrue(any(s['title'] == 'Continue The Grand Tree' for s in self.evaluate(account)['the_grand_tree']['next_steps']))
        # Completion is authoritative even if a boost was used at the obstacle.
        account['skill_levels']['agility'] = 24
        account['quest_progress']['the_grand_tree'] = 'completed'
        row = self.evaluate(account)['the_grand_tree']
        self.assertEqual(row['status'], 'completed')
        self.assertEqual(row['next_steps'], [])

    def test_members_travel_is_gated_and_f2p_travel_is_available(self):
        account = {'members': False, 'skill_levels': {'magic': 1, 'woodcutting': 1},
                   'milestones': {'chronicle_obtained': False},
                   'quest_progress': {'tree_gnome_village': 'not_started', 'the_grand_tree': 'not_started'}}
        rows = self.evaluate(account)
        for goal in ['magic_45', 'tree_gnome_village', 'the_grand_tree']:
            self.assertEqual([s['id'] for s in rows[goal]['next_steps']], ['membership'])
        for goal in ['magic_31', 'woodcutting_12', 'chronicle']:
            self.assertEqual(rows[goal]['next_steps'][0]['kind'], 'activity')
        result = recommend(load_catalog(), list(rows.values()),
                           {'interests': ['exploration', 'utility'], 'commitment': 'short'}, account)
        self.assertIn(result['recommended_goal_id'], ['woodcutting_12', 'chronicle'])

    def test_travel_choices_are_searchable_and_networks_remain_separate(self):
        rows = self.evaluate({})
        for goal in ['magic_25', 'magic_31', 'magic_37', 'magic_45', 'woodcutting_12',
                     'chronicle', 'tree_gnome_village', 'the_grand_tree', 'prince_ali_rescue', 'ardougne_cloak_1']:
            self.assertIn('travel', rows[goal]['goal']['summary'].lower())
        spirit = rows['tree_gnome_village']
        self.assertEqual([r['id'] for r in spirit['requirements']], ['membership', 'tree_gnome_village'])
        self.assertEqual(spirit['status'], 'unknown')
