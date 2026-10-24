package dev.beginnercompanion.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Shared asynchronous transport for the console and RuneLite panel. */
public final class PlannerClient {
    private static final Gson JSON = new Gson();

    public static final class Requirement {
        public String id, label, status;
        public com.google.gson.JsonElement observed, required;
    }

    public static final class Result {
        public int protocol_version, revision;
        public String session_id, goal_id, scope, dataset_version;
        public Requirement[] requirements;
        public String[] notes;
    }

    public static final class GoalDescription {
        public String id, title, summary, investment, reviewed;
        public boolean members_only;
        public String[] benefits, preparation, sources;
    }

    public static final class GoalRequirement {
        public String id, label, status, note, category;
        public com.google.gson.JsonElement observed, required;
        public GoalRequirement[] children;
    }

    public static final class Progress {
        public int satisfied, missing, unknown, total;
    }

    public static final class GoalResult {
        public GoalDescription goal;
        public String status;
        public Progress progress;
        public GoalRequirement[] requirements;
        public Recommendation recommendation;
        public NextStep[] next_steps;
        public RemainingInvestment remaining_investment;
    }

    public static final class RemainingInvestment {
        public String state, summary, caveat, commitment_band, commitment_reason;
        public String[] details;
    }

    public static final class NextStep {
        public String id, title, reason, kind;
    }

    public static final class Recommendation {
        public int rank;
        public boolean recommended;
        public String availability;
        public String[] reasons, considerations;
    }

    public static final class GoalsResult {
        public int protocol_version, revision;
        public String session_id, dataset_version;
        public String recommended_goal_id, recommendation_summary;
        public GoalResult[] goals;
        public String[] notes;
    }

    public static CompletableFuture<GoalsResult> evaluateGoals(HttpClient client, URI endpoint, String body) {
        JsonObject sent = JsonParser.parseString(body).getAsJsonObject();
        return exchange(client, endpoint, body).thenApply(json -> {
            GoalsResult result = JSON.fromJson(json, GoalsResult.class);
            if (result == null || result.protocol_version != 1
                || !sent.get("session_id").getAsString().equals(result.session_id)
                || result.revision != sent.get("revision").getAsInt()) {
                throw new IllegalStateException("Response version or snapshot identity does not match request.");
            }
            if (result.goals == null || result.goals.length == 0 || result.notes == null
                || result.recommendation_summary == null) {
                throw new IllegalStateException("Invalid goal browser response. Restart the updated Python service.");
            }
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (GoalResult row : result.goals) {
                if (row == null || row.goal == null || row.goal.id == null || !ids.add(row.goal.id)
                    || row.goal.title == null || row.goal.summary == null || row.goal.investment == null
                    || row.goal.benefits == null || row.goal.preparation == null || row.goal.sources == null
                    || row.progress == null || row.requirements == null || row.recommendation == null || row.next_steps == null
                    || row.remaining_investment == null || row.remaining_investment.summary == null
                    || row.remaining_investment.caveat == null || row.remaining_investment.details == null
                    || row.remaining_investment.commitment_reason == null
                    || !java.util.Arrays.asList("short", "few_sessions", "long", "unknown", "completed", "blocked")
                        .contains(row.remaining_investment.commitment_band)
                    || !java.util.Arrays.asList("completed", "access_blocked", "unknown", "final_step", "prerequisites", "training", "quest", "diary")
                        .contains(row.remaining_investment.state)
                    || row.recommendation.reasons == null || row.recommendation.considerations == null
                    || !java.util.Arrays.asList("available", "members_required", "membership_unknown", "completed")
                        .contains(row.recommendation.availability)
                    || !java.util.Arrays.asList("completed", "incomplete", "unknown").contains(row.status)) {
                    throw new IllegalStateException("Invalid goal entry from Python.");
                }
                for (GoalRequirement requirement : row.requirements) validateRequirement(requirement);
                for (String detail : row.remaining_investment.details) {
                    if (detail == null) throw new IllegalStateException("Invalid remaining investment detail from Python.");
                }
                for (NextStep step : row.next_steps) {
                    if (step == null || step.id == null || step.title == null || step.reason == null
                        || !java.util.Arrays.asList("activity", "check").contains(step.kind)) {
                        throw new IllegalStateException("Invalid next step from Python.");
                    }
                }
            }
            return result;
        });
    }

    private static void validateRequirement(GoalRequirement row) {
        if (row == null || row.label == null || row.children == null || row.note == null
            || !java.util.Arrays.asList("satisfied", "missing", "unknown").contains(row.status)) {
            throw new IllegalStateException("Invalid goal requirement from Python.");
        }
        for (GoalRequirement child : row.children) validateRequirement(child);
    }

    private static CompletableFuture<String> exchange(HttpClient client, URI endpoint, String body) {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    String message = "HTTP " + response.statusCode();
                    try {
                        JsonObject detail = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("error");
                        message += " [" + detail.get("code").getAsString() + "]: " + detail.get("message").getAsString();
                    } catch (RuntimeException ignored) { message += ": response was not a recognised API error."; }
                    throw new IllegalStateException(message);
                }
                return response.body();
            });
    }

    public static CompletableFuture<Result> evaluate(HttpClient client, URI endpoint, String body) {
        JsonObject sent = JsonParser.parseString(body).getAsJsonObject();
        return exchange(client, endpoint, body)
            .thenApply(json -> {
                Result result = JSON.fromJson(json, Result.class);
                if (result.protocol_version != 1 || result.revision != sent.get("revision").getAsInt()
                    || !sent.get("session_id").getAsString().equals(result.session_id)
                    || !sent.get("goal_id").getAsString().equals(result.goal_id)) {
                    throw new IllegalStateException("Response version or snapshot identity does not match request.");
                }
                if (result.requirements == null || result.notes == null
                    || !"equipment_requirements_only".equals(result.scope)) {
                    throw new IllegalStateException("Invalid evaluation response.");
                }
                return result;
            });
    }

}
