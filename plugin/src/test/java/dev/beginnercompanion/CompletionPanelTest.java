package dev.beginnercompanion;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompletionPanelTest {
    private void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    @Test public void completionIsSavedThenSentForNewRecommendationsAndIsolatedOnSwitch() throws Exception {
        AtomicReference<JsonObject> sent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/goals/evaluate", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            sent.set(request);
            JsonObject response = fixture("response");
            int level = request.getAsJsonObject("account").getAsJsonObject("skill_levels").get("attack").getAsInt();
            for (JsonElement element : response.getAsJsonArray("goals")) {
                JsonObject goal = element.getAsJsonObject();
                if (!goal.getAsJsonObject("goal").get("id").getAsString().equals("attack_20")) continue;
                goal.addProperty("status", level >= 20 ? "completed" : "incomplete");
            }
            response.add("session_id", request.get("session_id")); response.add("revision", request.get("revision"));
            byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
        Path folder = Files.createTempDirectory("companion-panel-history");
        Path state = folder.resolve("goals.json");
        AtomicReference<CompanionPanel> panel = new AtomicReference<>();
        try {
            edt(() -> {
                panel.set(new CompanionPanel(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/goals/evaluate"), new GoalSelectionStore(state)));
                panel.get().enableLiveMode(); panel.get().setLiveSnapshot(snapshot("one", 1));
            });
            await(() -> panel.get().selectionsReady() && panel.get().selectedGoals().history.incomplete.contains("attack_20"));
            assertEquals(0, sent.get().getAsJsonArray("completion_history").size());
            edt(() -> panel.get().setLiveSnapshot(snapshot("one", 20)));
            await(() -> panel.get().selectionsReady() && panel.get().selectedGoals().history.events.containsKey("attack_20")
                && sent.get().getAsJsonArray("completion_history").size() == 1);
            long timestamp = new GoalSelectionStore(state).load().get("live:one").history.events.get("attack_20");
            assertEquals("attack_20", sent.get().getAsJsonArray("completion_history").get(0).getAsJsonObject().get("goal_id").getAsString());
            edt(() -> panel.get().setLiveSnapshot(snapshot("two", 20)));
            await(() -> panel.get().selectionsReady() && panel.get().selectedGoals().history.completed.contains("attack_20"));
            assertEquals(0, sent.get().getAsJsonArray("completion_history").size());
            assertTrue(new GoalSelectionStore(state).load().get("live:two").history.events.isEmpty());
            edt(() -> panel.get().setLiveSnapshot(snapshot("one", 20)));
            await(() -> panel.get().selectionsReady() && "Connected to Python".equals(panel.get().connectionText()));
            assertEquals(Long.valueOf(timestamp), new GoalSelectionStore(state).load().get("live:one").history.events.get("attack_20"));
        } finally {
            if (panel.get() != null) edt(() -> panel.get().close());
            server.stop(0); Files.deleteIfExists(state); Files.deleteIfExists(folder);
        }
    }
    private static JsonObject fixture(String kind) {
        return JsonParser.parseReader(new java.io.InputStreamReader(CompletionPanelTest.class.getResourceAsStream(
            "/fixtures/goals-fresh." + kind + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static LiveSnapshotReader.Snapshot snapshot(String id, int attack) {
        JsonObject account = fixture("request").getAsJsonObject("account");
        account.getAsJsonObject("skill_levels").addProperty("attack", attack);
        return new LiveSnapshotReader.Snapshot("live:" + id, "Synthetic", account);
    }
    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 120; i++) {
            AtomicBoolean ready = new AtomicBoolean(); edt(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for completion tracking");
    }
}
