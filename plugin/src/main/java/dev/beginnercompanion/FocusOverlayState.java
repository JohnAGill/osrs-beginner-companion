package dev.beginnercompanion;

import dev.beginnercompanion.bridge.PlannerClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable presentation snapshot passed from Swing to the game renderer. */
final class FocusOverlayState {
    static final FocusOverlayState HIDDEN = new FocusOverlayState(null, "", "", "", Collections.emptyList());
    final String localKey, title, status, progress;
    final List<String> checks;

    private FocusOverlayState(String localKey, String title, String status, String progress, List<String> checks) {
        this.localKey = localKey; this.title = title; this.status = status; this.progress = progress;
        this.checks = Collections.unmodifiableList(new ArrayList<>(checks));
    }

    static FocusOverlayState from(String key, String title, PlannerClient.GoalResult result, String unavailable) {
        return from(key, title, result, unavailable, null);
    }

    static FocusOverlayState from(String key, String title, PlannerClient.GoalResult result, String unavailable, GoalSelection selection) {
        if (selection != null && selection.taskId != null) {
            String taskStatus = result == null ? unavailable : CurrentTask.status(selection, result);
            List<String> task = new ArrayList<>();
            task.add("Current task: " + selection.taskTitle);
            task.add("Task: " + taskStatus);
            FocusOverlayState goal = from(key, title, result, unavailable);
            return new FocusOverlayState(key, goal.title, goal.status, goal.progress, task);
        }
        if (result == null) return new FocusOverlayState(key, title, unavailable, "", Collections.emptyList());
        String status = "completed".equals(result.status) ? "Completed" : "unknown".equals(result.status)
            ? "Completion unknown" : "In progress";
        String summary = result.progress.satisfied + " / " + result.progress.total + " milestones met";
        if (result.progress.unknown > 0) summary += " (" + result.progress.unknown + " unknown)";
        List<String> checks = new ArrayList<>();
        if (!"completed".equals(result.status) && result.next_steps != null) {
            for (PlannerClient.NextStep step : result.next_steps) {
                if (checks.size() >= 2) break;
                checks.add(("check".equals(step.kind) ? "Check: " : "Option: ")
                    + (step.title.startsWith("Check: ") ? step.title.substring(7) : step.title));
            }
        }
        return new FocusOverlayState(key, result.goal.title, status, summary, checks);
    }
}
