package dev.beginnercompanion;

import dev.beginnercompanion.bridge.PlannerClient;
import java.util.*;

/** First-seen completions form a baseline; only confirmed incomplete -> complete is recent. */
final class CompletionHistory {
    final Set<String> completed, incomplete;
    final Map<String, Long> events;
    CompletionHistory(Set<String> completed, Set<String> incomplete, Map<String, Long> events) {
        for (String id : completed) validateId(id);
        for (String id : incomplete) validateId(id);
        for (Map.Entry<String, Long> event : events.entrySet()) {
            validateId(event.getKey());
            if (!completed.contains(event.getKey()) || event.getValue() < 0 || event.getValue() > 253402300799L) throw new IllegalArgumentException("Invalid completion event");
        }
        if (!Collections.disjoint(completed, incomplete)) throw new IllegalArgumentException("Contradictory history baseline");
        this.completed = Collections.unmodifiableSet(new LinkedHashSet<>(completed));
        this.incomplete = Collections.unmodifiableSet(new LinkedHashSet<>(incomplete));
        this.events = Collections.unmodifiableMap(new LinkedHashMap<>(events));
    }
    static void validateId(String id) {
        if (id == null || !id.matches("[a-z0-9_]{1,128}")) throw new IllegalArgumentException("Invalid history goal");
    }
    static CompletionHistory empty() { return new CompletionHistory(Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()); }
    CompletionHistory observe(PlannerClient.GoalsResult snapshot, long now) {
        Set<String> done = new LinkedHashSet<>(completed), pending = new LinkedHashSet<>(incomplete);
        Map<String, Long> next = new LinkedHashMap<>(events);
        for (PlannerClient.GoalResult goal : snapshot.goals) {
            String id = goal.goal.id;
            if ("completed".equals(goal.status) && done.add(id)) {
                if (pending.remove(id)) next.put(id, now);
            } else if ("incomplete".equals(goal.status) && !done.contains(id)) pending.add(id);
        }
        if (done.equals(completed) && pending.equals(incomplete) && next.equals(events)) return this;
        return new CompletionHistory(done, pending, next);
    }
}
