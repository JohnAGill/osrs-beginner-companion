package dev.beginnercompanion;

import java.util.Objects;

final class GoalFeedback {
    final String kind;
    final long recordedAt;
    GoalFeedback(String kind, long recordedAt) {
        if (!java.util.Arrays.asList("not_interested", "maybe_later", "too_much_work").contains(kind)
            || recordedAt < 0 || recordedAt > 253402300799L) throw new IllegalArgumentException("Invalid feedback");
        this.kind = kind; this.recordedAt = recordedAt;
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof GoalFeedback)) return false;
        GoalFeedback value = (GoalFeedback) other;
        return kind.equals(value.kind) && recordedAt == value.recordedAt;
    }
    @Override public int hashCode() { return Objects.hash(kind, recordedAt); }
    long expiresAt() {
        return "not_interested".equals(kind) ? Long.MAX_VALUE : recordedAt + ("maybe_later".equals(kind) ? 86400 : 7 * 86400);
    }
}
