package dev.beginnercompanion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class GoalSelectionStoreTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void pinSaveUnpinAndRemoveHaveConsistentSemantics() {
        GoalSelection initial = GoalSelection.empty();
        GoalSelection rune = initial.pin("rune", "Rune platebody");
        assertTrue(initial.saved.isEmpty());
        assertEquals("rune", rune.focus);
        GoalSelection cloak = rune.pin("cloak", "Ardougne cloak");
        assertEquals(2, cloak.saved.size());
        assertEquals("cloak", cloak.focus);
        assertNull(cloak.unpin().focus);
        assertEquals(2, cloak.unpin().saved.size());
        assertNull(cloak.remove("cloak").focus);
        assertTrue(cloak.remove("cloak").saved.containsKey("rune"));
        assertEquals("cloak", cloak.remove("rune").focus);
    }

    @Test public void storesDistinctAccountsAndRestoresInNewInstance() throws Exception {
        Path path = temp.getRoot().toPath().resolve("nested/goals.json");
        GoalSelectionStore store = new GoalSelectionStore(path);
        assertTrue(store.load().isEmpty());
        Map<String, GoalSelection> accounts = new LinkedHashMap<>();
        accounts.put("fresh", GoalSelection.empty().pin("rune", "Rune platebody"));
        accounts.put("partial", GoalSelection.empty().save("cloak", "Ardougne cloak"));
        store.save(accounts);
        Map<String, GoalSelection> restored = new GoalSelectionStore(path).load();
        assertEquals("rune", restored.get("fresh").focus);
        assertTrue(restored.get("partial").saved.containsKey("cloak"));
        assertNull(restored.get("partial").focus);
        assertFalse(restored.get("partial").saved.containsKey("rune"));
        accounts.put("fresh", accounts.get("fresh").unpin());
        store.save(accounts); // Exercises atomic replacement of an existing file on Windows.
        assertNull(store.load().get("fresh").focus);
        try (java.util.stream.Stream<Path> siblings = Files.list(path.getParent())) {
            assertEquals(1, siblings.count());
        }
    }

    @Test public void malformedOrUnsupportedStateIsNeverSilentlyReplaced() throws Exception {
        Path path = temp.getRoot().toPath().resolve("goals.json");
        for (String data : new String[]{"{", "{\"version\":2,\"accounts\":{}}",
            "{\"version\":1,\"accounts\":{\"fresh\":{\"saved_goals\":{},\"focus_goal_id\":\"missing\"}}}"}) {
            Files.writeString(path, data);
            assertThrows(java.io.IOException.class, () -> new GoalSelectionStore(path).load());
            assertEquals(data, Files.readString(path));
        }
        Path blocked = temp.getRoot().toPath().resolve("not-a-directory");
        Files.writeString(blocked, "keep me");
        assertThrows(java.io.IOException.class, () -> new GoalSelectionStore(blocked.resolve("goals.json")).save(new LinkedHashMap<>()));
        assertEquals("keep me", Files.readString(blocked));
    }

    @Test public void taskPersistsAndStaysWithItsPinnedGoal() throws Exception {
        Path path = temp.getRoot().toPath().resolve("tasks.json");
        GoalSelection chosen = GoalSelection.empty().pin("rune", "Rune platebody").task("defence_40", "Work towards 40 Defence");
        Map<String, GoalSelection> accounts = new LinkedHashMap<>();
        accounts.put("fresh", chosen);
        accounts.put("partial", GoalSelection.empty().pin("rune", "Rune platebody"));
        new GoalSelectionStore(path).save(accounts);
        GoalSelection restored = new GoalSelectionStore(path).load().get("fresh");
        assertEquals("defence_40", restored.taskId);
        assertEquals("Work towards 40 Defence", restored.taskTitle);
        assertNull(new GoalSelectionStore(path).load().get("partial").taskId);
        assertEquals("defence_40", chosen.save("cloak", "Cloak").taskId);
        assertEquals("defence_40", chosen.pin("rune", "Rune platebody").taskId);
        assertEquals("defence_40", chosen.remove("other").taskId);
        assertNull(chosen.pin("cloak", "Cloak").taskId);
        assertNull(chosen.remove("rune").taskId);
        assertNull(chosen.unpin().taskId);
        assertEquals("rune", chosen.clearTask().focus);
        assertNull(chosen.clearTask().taskId);
        // Existing version 1 files have no task fields and remain readable.
        Files.writeString(path, "{\"version\":1,\"accounts\":{\"fresh\":{\"saved_goals\":{\"rune\":\"Rune\"},\"focus_goal_id\":\"rune\"}}}");
        assertNull(new GoalSelectionStore(path).load().get("fresh").taskId);
    }

    @Test public void feedbackPersistsPerCharacterAndResetsWithoutChangingOtherChoices() throws Exception {
        Path path = temp.getRoot().toPath().resolve("feedback.json");
        GoalSelection original = GoalSelection.empty().pin("rune", "Rune").task("defence_40", "Defence")
            .feedback("rune", "not_interested", 100).feedback("cloak", "maybe_later", 200)
            .feedback("quest", "too_much_work", 300);
        Map<String, GoalSelection> accounts = new LinkedHashMap<>();
        accounts.put("one", original); accounts.put("two", GoalSelection.empty());
        new GoalSelectionStore(path).save(accounts);
        Map<String, GoalSelection> restored = new GoalSelectionStore(path).load();
        assertEquals(original.feedback, restored.get("one").feedback);
        assertTrue(restored.get("two").feedback.isEmpty());
        assertEquals(2, original.resetFeedback("rune").feedback.size());
        GoalSelection reset = original.resetFeedback(null);
        assertTrue(reset.feedback.isEmpty()); assertEquals("rune", reset.focus); assertEquals("defence_40", reset.taskId);
        assertSame(original.history, reset.history);
        assertEquals(original.feedback, original.unpin().save("other", "Other").remove("other").withHistory(original.history).feedback);
        assertThrows(UnsupportedOperationException.class, () -> original.feedback.clear());
        assertThrows(IllegalArgumentException.class, () -> original.feedback("x", "invalid", 1));
    }
}
