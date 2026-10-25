package dev.beginnercompanion;

import dev.beginnercompanion.bridge.PlannerClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompletionHistoryTest {
    private PlannerClient.GoalsResult snapshot(String... statuses) {
        PlannerClient.GoalsResult result = new PlannerClient.GoalsResult();
        result.goals = new PlannerClient.GoalResult[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            result.goals[i] = new PlannerClient.GoalResult();
            result.goals[i].goal = new PlannerClient.GoalDescription();
            result.goals[i].goal.id = "goal_" + i;
            result.goals[i].status = statuses[i];
        }
        return result;
    }

    @Test public void baselineUnknownTransitionsAndRepeatedSnapshotsDoNotInventRecency() {
        CompletionHistory first = CompletionHistory.empty().observe(snapshot("completed", "unknown", "incomplete"), 100);
        assertTrue(first.events.isEmpty());
        CompletionHistory second = first.observe(snapshot("completed", "completed", "unknown"), 200);
        assertTrue(second.events.isEmpty());
        CompletionHistory third = second.observe(snapshot("completed", "completed", "completed"), 300);
        assertEquals(Collections.singletonMap("goal_2", 300L), third.events);
        assertSame(third, third.observe(snapshot("completed", "completed", "completed"), 400));
        CompletionHistory regression = third.observe(snapshot("completed", "completed", "incomplete"), 500);
        assertSame(third, regression.observe(snapshot("completed", "completed", "completed"), 600));
        assertThrows(UnsupportedOperationException.class, () -> third.events.put("goal_0", 1L));
    }

    @Test public void historySurvivesStorageAndChoiceChangesWithoutLeakingBetweenAccounts() throws Exception {
        Path path = Files.createTempDirectory("companion-history-test").resolve("goals.json");
        try {
            CompletionHistory history = CompletionHistory.empty().observe(snapshot("incomplete"), 100)
                .observe(snapshot("completed"), 200);
            GoalSelection chosen = GoalSelection.empty().withHistory(history).pin("goal_0", "Goal")
                .task("some_task", "Some task").clearTask().save("other", "Other").remove("other").unpin();
            Map<String, GoalSelection> accounts = new LinkedHashMap<>();
            accounts.put("one", chosen); accounts.put("two", GoalSelection.empty());
            TestPanels.store(path).save(accounts);
            Map<String, GoalSelection> restored = TestPanels.store(path).load();
            assertEquals(history.events, restored.get("one").history.events);
            assertEquals(history.completed, restored.get("one").history.completed);
            assertTrue(restored.get("two").history.events.isEmpty());
            assertSame(restored.get("one").history,
                restored.get("one").history.observe(snapshot("completed"), 300));
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(path.getParent()); }
    }
}
