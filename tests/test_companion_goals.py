import copy
import json
from pathlib import Path
import unittest
from beginner_companion.domain.catalog import load_catalog, evaluate_goals

ROOT = Path(__file__).resolve().parents[1]


def demo(name):
    return json.loads((ROOT / f'contracts/fixtures/goals-{name}.request.json').read_text(encoding="utf-8"))


class GoalDomainTests(unittest.TestCase):
    def setUp(self):
        self.catalog = load_catalog()

    def results(self, account):
        return {row['goal']['id']: row for row in evaluate_goals(self.catalog, account)}

    def test_complete_and_unknown(self):
        completed = self.results(demo('complete')['account'])
        self.assertEqual(len(completed), 33)
        for row in completed.values():
            self.assertEqual(row['status'], 'completed')
            self.assertEqual(row['progress']['satisfied'], row['progress']['total'])
        for row in self.results({}).values():
            self.assertEqual(row['status'], 'unknown')
            self.assertEqual(row['progress']['unknown'], row['progress']['total'])

    def test_unlocks_do_not_imply_wearing_or_claiming(self):
        results = self.results(demo('unclaimed')['account'])
        self.assertEqual(results['wear_rune_platebody']['status'], 'incomplete')
        cloak = results['ardougne_cloak_1']
        self.assertEqual(cloak['status'], 'incomplete')
        tasks = next(row for row in cloak['requirements'] if row['id'] == 'easy_tasks')
        self.assertEqual(tasks['status'], 'satisfied')
        self.assertEqual(len(tasks['children']), 10)
        self.assertEqual(results['prince_ali_rescue']['status'], 'completed')

    def test_partial_diary_and_membership_are_explicit(self):
        cloak = self.results(demo('partial')['account'])['ardougne_cloak_1']
        tasks = next(row for row in cloak['requirements'] if row['id'] == 'easy_tasks')
        self.assertEqual(tasks['observed'], '4 / 10 tasks done')
        self.assertEqual(tasks['status'], 'missing')
        fresh = self.results(demo('fresh')['account'])['ardougne_cloak_1']
        self.assertEqual(fresh['requirements'][0]['status'], 'missing')
        self.assertEqual(fresh['requirements'][0]['category'], 'eligibility')
        self.assertEqual(fresh['progress']['total'], 6)  # excludes membership

    def test_partial_unknown_group_and_thresholds(self):
        account = demo('partial')['account']
        account['ardougne_easy_tasks'] = {key: True for key in account['ardougne_easy_tasks']}
        del account['ardougne_easy_tasks']['pet_check']
        result = self.results(account)
        tasks = next(row for row in result['ardougne_cloak_1']['requirements'] if row['id'] == 'easy_tasks')
        self.assertEqual(tasks['status'], 'unknown')
        account['skill_levels']['defence'] = 40
        account['quest_points'] = 32
        rune = self.results(account)['wear_rune_platebody']
        self.assertEqual(rune['requirements'][0]['status'], 'satisfied')
        self.assertEqual(rune['requirements'][2]['status'], 'satisfied')
        self.assertEqual(rune['requirements'][1]['status'], 'missing')

    def test_deterministic_and_no_mutation(self):
        account = demo('partial')['account']
        before = copy.deepcopy(account)
        self.assertEqual(self.results(account), self.results(account))
        self.assertEqual(account, before)
        for goal in self.catalog['goals']:
            self.assertTrue(goal['sources'])
            self.assertTrue(goal['reviewed'])


try:
    from fastapi.testclient import TestClient
    from beginner_companion.api.app import app
    from beginner_companion.api.goals import GoalsRequest, GoalsResponse, CatalogResponse
except ImportError:
    TestClient = None


@unittest.skipIf(TestClient is None, 'Install optional API dependencies')
class GoalApiTests(unittest.TestCase):
    def test_catalog_and_fixtures(self):
        with TestClient(app) as client:
            catalog = client.get('/v1/catalog')
            self.assertEqual(catalog.status_code, 200)
            self.assertEqual(len(catalog.json()['goals']), 33)
            for name in ('fresh', 'partial', 'complete', 'unclaimed', 'unknown'):
                response = client.post('/v1/goals/evaluate', json=demo(name))
                self.assertEqual(response.status_code, 200, response.text)
                expected = json.loads((ROOT / f'contracts/fixtures/goals-{name}.response.json').read_text(encoding="utf-8"))
                self.assertEqual(response.json(), expected)

    def test_validation_and_version(self):
        with TestClient(app) as client:
            payload = demo('fresh')
            payload['protocol_version'] = 2
            self.assertEqual(client.post('/v1/goals/evaluate', json=payload).status_code, 409)
            for patch in ({'members': 'false'}, {'quest_points': True},
                          {'ardougne_easy_tasks': {'invalid_task': True}},
                          {'milestones': {'ardougne_cloak_1_claimed': 'yes'}}):
                payload = demo('fresh')
                payload['account'].update(patch)
                self.assertEqual(client.post('/v1/goals/evaluate', json=payload).status_code, 422)

    def test_schema_exports(self):
        for name, model in [('goals-request', GoalsRequest), ('goals-response', GoalsResponse),
                            ('catalog-response', CatalogResponse)]:
            self.assertEqual(json.loads((ROOT / f'contracts/{name}.schema.json').read_text(encoding="utf-8")), model.model_json_schema())
