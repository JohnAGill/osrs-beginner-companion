package dev.beginnercompanion;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecommendationFeedbackPanelTest {
    private void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    @Test public void feedbackRoundTripHidesOnlyExploreSurvivesRestartAndResets() throws Exception {
        AtomicReference<JsonObject> sent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/goals/evaluate", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject response = JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream(
                "/fixtures/goals-fresh.response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject feedback = request.getAsJsonObject("recommendation_feedback");
            for (JsonElement element : response.getAsJsonArray("goals")) {
                JsonObject goal = element.getAsJsonObject();
                String id = goal.getAsJsonObject("goal").get("id").getAsString();
                if (feedback.has(id)) {
                    goal.getAsJsonObject("recommendation").addProperty("feedback_status", feedback.getAsJsonObject(id).get("kind").getAsString());
                    goal.getAsJsonObject("recommendation").addProperty("feedback_reason", "Paused by your feedback.");
                }
            }
            sent.set(request);
            response.add("session_id", request.get("session_id")); response.add("revision", request.get("revision"));
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); }
            finally { exchange.close(); }
        });
        server.start();
        Path folder = Files.createTempDirectory("companion-feedback"); Path state = folder.resolve("goals.json");
        Map<String, GoalSelection> choices = new LinkedHashMap<>();
        choices.put("fresh", GoalSelection.empty().pin("wear_rune_platebody", "Wear a rune platebody").task("defence_40", "Defence"));
        new GoalSelectionStore(state).save(choices);
        AtomicReference<CompanionPanel> ref = new AtomicReference<>();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/goals/evaluate");
        try {
            edt(() -> { ref.set(new CompanionPanel(endpoint, new GoalSelectionStore(state))); ref.get().setPreferences(new String[]{"combat"}, 3); ref.get().evaluate(); });
            ready(ref);
            edt(() -> { ref.get().selectGoal("wear_rune_platebody"); assertTrue(click(ref.get(), "▸ Recommendation feedback")); assertTrue(click(ref.get(), "Not interested")); });
            await(() -> ref.get().selectionsReady() && sent.get().getAsJsonObject("recommendation_feedback").has("wear_rune_platebody")
                && "Connected to Python".equals(ref.get().connectionText()));
            assertEquals("long", sent.get().getAsJsonObject("preferences").get("commitment").getAsString());
            assertEquals("combat", sent.get().getAsJsonObject("preferences").getAsJsonArray("interests").get(0).getAsString());
            edt(() -> {
                ref.get().showBrowser(); assertFalse(ref.get().visibleText().contains("Wear a rune platebody"));
                ref.get().showFocus(); assertEquals("wear_rune_platebody", ref.get().selectedGoals().focus);
                assertEquals("defence_40", ref.get().selectedGoals().taskId);
                assertTrue(ref.get().visibleText().contains("Wear a rune platebody"));
                assertTrue(click(ref.get(), "▸ Settings")); assertTrue(click(ref.get(), "▸ Recommendation feedback"));
                assertTrue(click(ref.get(), "Reset all feedback"));
            });
            await(() -> ref.get().selectionsReady() && sent.get().getAsJsonObject("recommendation_feedback").size() == 0
                && "Connected to Python".equals(ref.get().connectionText()));
            edt(() -> { ref.get().selectGoal("wear_rune_platebody"); assertTrue(click(ref.get(), "Maybe later")); });
            await(() -> ref.get().selectionsReady() && "maybe_later".equals(ref.get().selectedGoals().feedback.get("wear_rune_platebody").kind)
                && "Connected to Python".equals(ref.get().connectionText()));
            edt(() -> {
                ref.get().showBrowser(); assertTrue(ref.get().visibleText().contains("Wear a rune platebody"));
                assertTrue(ref.get().visibleText().contains("Paused by your feedback"));
                ref.get().selectDemo(1); assertTrue(ref.get().selectedGoals().feedback.isEmpty());
                ref.get().selectDemo(0); assertEquals("maybe_later", ref.get().selectedGoals().feedback.get("wear_rune_platebody").kind);
                ref.get().close(); ref.set(new CompanionPanel(endpoint, new GoalSelectionStore(state)));
            });
            await(() -> ref.get().selectionsReady());
            edt(() -> {
                assertEquals("maybe_later", ref.get().selectedGoals().feedback.get("wear_rune_platebody").kind);
                ref.get().evaluate();
            });
            ready(ref);
            edt(() -> { ref.get().selectGoal("wear_rune_platebody"); assertTrue(click(ref.get(), "▸ Recommendation feedback")); assertTrue(click(ref.get(), "Too much work right now")); });
            await(() -> ref.get().selectionsReady() && "too_much_work".equals(ref.get().selectedGoals().feedback.get("wear_rune_platebody").kind)
                && "Connected to Python".equals(ref.get().connectionText()));
            edt(() -> assertTrue(click(ref.get(), "Reset feedback for this goal")));
            await(() -> ref.get().selectionsReady() && ref.get().selectedGoals().feedback.isEmpty() && "Connected to Python".equals(ref.get().connectionText()));
            assertTrue(new GoalSelectionStore(state).load().get("fresh").feedback.isEmpty());
        } finally {
            if (ref.get() != null) edt(() -> ref.get().close());
            server.stop(0); Files.deleteIfExists(state); Files.deleteIfExists(folder);
        }
    }
    private void ready(AtomicReference<CompanionPanel> panel) throws Exception {
        await(() -> panel.get().selectionsReady() && "Connected to Python".equals(panel.get().connectionText()));
    }
    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 140; i++) {
            AtomicBoolean ready = new AtomicBoolean(); edt(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return; Thread.sleep(50);
        }
        fail("Timed out waiting for feedback");
    }
    private boolean click(Container parent, String label) {
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof AbstractButton && label.equals(((AbstractButton) child).getText())) { ((AbstractButton) child).doClick(); return true; }
            if (child instanceof Container && click((Container) child, label)) return true;
        }
        return false;
    }
}
