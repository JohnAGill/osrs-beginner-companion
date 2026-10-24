import dev.beginnercompanion.bridge.PlannerClient;
import dev.beginnercompanion.bridge.PlannerClient.Result;
import dev.beginnercompanion.bridge.PlannerClient.Requirement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Standalone caller; no RuneLite or game login required. Requires Java 11+. */
public final class CompanionCaller {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: CompanionCaller <request.json> [http://127.0.0.1:8765]");
            System.exit(2);
        }
        try {
            URI base = URI.create(args.length == 2 ? args[1] : "http://127.0.0.1:8765");
            if (!"http".equals(base.getScheme()) || !"127.0.0.1".equals(base.getHost())
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
                throw new IllegalArgumentException("Service URL must be http://127.0.0.1:<port>.");
            }
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            String body = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
            // Blocking only at this console boundary. UI callers use the returned future.
            if ("all".equals(JsonParser.parseString(body).getAsJsonObject().get("goal_id").getAsString())) {
                PlannerClient.GoalsResult browser = PlannerClient.evaluateGoals(client, base.resolve("/v1/goals/evaluate"), body).join();
                for (PlannerClient.GoalResult goal : browser.goals) {
                    System.out.printf("%s: %s (%d/%d milestones, %d unknown)%n", goal.goal.id,
                        goal.status, goal.progress.satisfied, goal.progress.total, goal.progress.unknown);
                }
                System.out.println("Recommendation: " + (browser.recommended_goal_id == null ? "none" : browser.recommended_goal_id));
                System.out.println(browser.recommendation_summary);
                for (PlannerClient.GoalResult goal : browser.goals) {
                    for (String reason : goal.recommendation.reasons) System.out.println(goal.goal.title + ": " + reason);
                    for (String note : goal.recommendation.considerations) System.out.println(goal.goal.title + ": " + note);
                }
                return;
            }
            Result result = PlannerClient.evaluate(client, base.resolve("/v1/evaluate"), body).join();
            System.out.println("Python evaluation: " + result.goal_id + " (" + result.dataset_version + ")");
            for (Requirement row : result.requirements) {
                System.out.printf("%s: %s (observed=%s, required=%s)%n",
                    row.status, row.label, row.observed, row.required);
            }
            for (String note : result.notes) System.out.println(note);
        } catch (Exception exception) {
            Throwable cause = exception;
            while (cause.getCause() != null) cause = cause.getCause();
            System.err.println("Bridge failed: " + cause.getClass().getSimpleName()
                + ": " + (cause.getMessage() == null ? "Cannot reach local Python service; check it is running." : cause.getMessage()));
            System.exit(1);
        }
    }
}
