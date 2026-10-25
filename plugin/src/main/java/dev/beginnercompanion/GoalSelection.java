package dev.beginnercompanion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable player choices. Pinning saves a goal; removing it also clears its focus. */
final class GoalSelection {
    final Map<String, String> saved;
    final String focus;
    final String taskId, taskTitle;
    final CompletionHistory history;
    final Map<String, GoalFeedback> feedback;

    GoalSelection(Map<String, String> saved, String focus) {
        this(saved, focus, null, null);
    }

    GoalSelection(Map<String, String> saved, String focus, String taskId, String taskTitle) {
        this(saved, focus, taskId, taskTitle, CompletionHistory.empty());
    }
    GoalSelection(Map<String, String> saved, String focus, String taskId, String taskTitle, CompletionHistory history) {
        this(saved, focus, taskId, taskTitle, history, Collections.emptyMap());
    }
    GoalSelection(Map<String, String> saved, String focus, String taskId, String taskTitle, CompletionHistory history, Map<String, GoalFeedback> feedback) {
        this.saved = Collections.unmodifiableMap(new LinkedHashMap<>(saved));
        if (focus != null && !saved.containsKey(focus)) throw new IllegalArgumentException("Focus must be saved");
        this.focus = focus;
        if ((taskId == null) != (taskTitle == null) || (taskId != null && (focus == null
            || !taskId.matches("[a-z0-9_]{1,128}") || taskTitle.isBlank() || taskTitle.length() > 512))) {
            throw new IllegalArgumentException("Invalid current task");
        }
        this.taskId = taskId; this.taskTitle = taskTitle;
        this.history = history;
        if (feedback.size() > 100) throw new IllegalArgumentException("Too much feedback");
        feedback.forEach((id, value) -> { CompletionHistory.validateId(id); Objects.requireNonNull(value); });
        this.feedback = Collections.unmodifiableMap(new LinkedHashMap<>(feedback));
    }

    static GoalSelection empty() { return new GoalSelection(Collections.emptyMap(), null); }

    GoalSelection save(String id, String title) {
        Map<String, String> next = new LinkedHashMap<>(saved);
        next.put(id, title);
        return new GoalSelection(next, focus, taskId, taskTitle, history, feedback);
    }

    GoalSelection remove(String id) {
        Map<String, String> next = new LinkedHashMap<>(saved);
        next.remove(id);
        return id.equals(focus) ? new GoalSelection(next, null, null, null, history, feedback) : new GoalSelection(next, focus, taskId, taskTitle, history, feedback);
    }

    GoalSelection pin(String id, String title) {
        return new GoalSelection(save(id, title).saved, id,
            id.equals(focus) ? taskId : null, id.equals(focus) ? taskTitle : null, history, feedback);
    }

    GoalSelection unpin() { return new GoalSelection(saved, null, null, null, history, feedback); }

    GoalSelection task(String id, String title) { return new GoalSelection(saved, focus, id, title, history, feedback); }
    GoalSelection clearTask() { return new GoalSelection(saved, focus, null, null, history, feedback); }
    GoalSelection withHistory(CompletionHistory next) { return new GoalSelection(saved, focus, taskId, taskTitle, next, feedback); }
    GoalSelection feedback(String goal, String kind, long now) {
        Map<String, GoalFeedback> next = new LinkedHashMap<>(feedback);
        next.put(goal, new GoalFeedback(kind, now));
        return new GoalSelection(saved, focus, taskId, taskTitle, history, next);
    }
    GoalSelection resetFeedback(String goal) {
        Map<String, GoalFeedback> next = new LinkedHashMap<>(feedback);
        if (goal == null) next.clear(); else next.remove(goal);
        return new GoalSelection(saved, focus, taskId, taskTitle, history, next);
    }
}
