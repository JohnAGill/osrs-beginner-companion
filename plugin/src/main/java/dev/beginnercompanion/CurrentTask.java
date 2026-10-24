package dev.beginnercompanion;

import dev.beginnercompanion.bridge.PlannerClient;

/** Resolve a saved choice against fresh observations; never substitute another task. */
final class CurrentTask {
    static String status(GoalSelection selection, PlannerClient.GoalResult goal) {
        if (goal == null) return "Progress unavailable";
        if ("completed".equals(goal.status) || satisfied(goal.requirements, selection.taskId)) return "Completed";
        if (goal.next_steps != null) for (PlannerClient.NextStep step : goal.next_steps) {
            if (step.id.equals(selection.taskId)) return "check".equals(step.kind) ? "Needs an in-game check" : "In progress";
        }
        return "Review task: no longer suggested";
    }

    private static boolean satisfied(PlannerClient.GoalRequirement[] rows, String id) {
        if (rows != null) for (PlannerClient.GoalRequirement row : rows) {
            if (row.id.equals(id) && "satisfied".equals(row.status)) return true;
            if (satisfied(row.children, id)) return true;
        }
        return false;
    }
}
