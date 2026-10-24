package dev.beginnercompanion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
import java.awt.Container;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SavedGoalsPanelTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    @Test public void savePinSwitchAccountsAndRestoreOffline() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/goals/evaluate", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            String fixture = request.getAsJsonObject("account").get("members").getAsBoolean() ? "partial" : "fresh";
            JsonObject response = JsonParser.parseReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/fixtures/goals-" + fixture + ".response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            response.add("session_id", request.get("session_id"));
            response.add("revision", request.get("revision"));
            byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
        });
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/goals/evaluate");
        Path path = temp.getRoot().toPath().resolve("goals.json");
        AtomicReference<CompanionPanel> ref = new AtomicReference<>();
        try {
            edt(() -> ref.set(new CompanionPanel(endpoint, new GoalSelectionStore(path))));
            ready(ref.get());
            edt(() -> { ref.get().showSaved(); assertTrue(ref.get().visibleText().contains("No saved goals yet")); ref.get().evaluate(); });
            connected(ref.get());
            edt(() -> { ref.get().selectGoal("wear_rune_platebody"); assertTrue(click(ref.get(), "Save goal")); });
            ready(ref.get());
            edt(() -> {
                assertTrue(ref.get().selectedGoals().saved.containsKey("wear_rune_platebody"));
                assertNull(ref.get().selectedGoals().focus);
                assertTrue(click(ref.get(), "Pin as focus"));
                // Switch accounts before disk completion; the write must stay with fresh.
                ref.get().selectDemo(1);
            });
            ready(ref.get());
            edt(() -> {
                assertTrue(ref.get().selectedGoals().saved.isEmpty());
                ref.get().selectDemo(0);
                assertEquals("wear_rune_platebody", ref.get().selectedGoals().focus);
                assertTrue(ref.get().selectionText().contains("refresh progress"));
                ref.get().evaluate();
            });
            connected(ref.get());
            edt(() -> {
                assertTrue(ref.get().selectionText().contains("Suggested next steps"));
                assertTrue(ref.get().selectionText().contains("Work towards 40 Defence"));
                assertTrue(ref.get().selectionText().contains("Choose quests towards 32 quest points"));
                ref.get().showFocus();
                assertTrue(click(ref.get(), "Work on this"));
            });
            ready(ref.get());
            edt(() -> {
                assertEquals("defence_40", ref.get().selectedGoals().taskId);
                assertEquals("wear_rune_platebody", ref.get().selectedGoals().focus);
                assertTrue(ref.get().selectionText().contains("Current task: Work towards 40 Defence"));
                assertTrue(ref.get().visibleText().contains("Current task: Work towards 40 Defence"));
                assertFalse(ref.get().visibleText().contains("Explore goals"));
                ref.get().showBrowser();
                assertFalse(ref.get().visibleText().contains("Current task:"));
                assertTrue(ref.get().visibleText().contains("Explore goals"));
                ref.get().showFocus();
                assertTrue(ref.get().visibleText().contains("Current task: Work towards 40 Defence"));
                assertTrue(click(ref.get(), "Clear current task"));
            });
            ready(ref.get());
            edt(() -> {
                assertNull(ref.get().selectedGoals().taskId);
                assertEquals("wear_rune_platebody", ref.get().selectedGoals().focus);
                ref.get().selectGoal("ardougne_cloak_1"); assertTrue(click(ref.get(), "Pin as focus"));
            });
            ready(ref.get());
            edt(() -> {
                assertEquals("ardougne_cloak_1", ref.get().selectedGoals().focus);
                assertTrue(ref.get().selectionText().contains("Keep this as a future members goal"));
                assertFalse(ref.get().selectionText().contains("Work towards 40 Defence"));
                assertEquals(2, ref.get().selectedGoals().saved.size());
                assertTrue(click(ref.get(), "Unpin focus"));
            });
            ready(ref.get());
            edt(() -> { assertNull(ref.get().selectedGoals().focus); assertEquals(2, ref.get().selectedGoals().saved.size()); assertTrue(click(ref.get(), "Pin as focus")); });
            ready(ref.get());
            edt(() -> {
                ref.get().selectGoal("ardougne_cloak_1");
                assertTrue(click(ref.get(), "Remove saved goal"));
            });
            ready(ref.get());
            edt(() -> {
                assertNull(ref.get().selectedGoals().focus);
                assertEquals(1, ref.get().selectedGoals().saved.size());
                ref.get().selectGoal("wear_rune_platebody"); assertTrue(click(ref.get(), "Pin as focus"));
            });
            ready(ref.get());
            edt(() -> { ref.get().selectDemo(1); ref.get().evaluate(); });
            connected(ref.get());
            edt(() -> { ref.get().selectGoal("prince_ali_rescue"); assertTrue(click(ref.get(), "Pin as focus")); });
            ready(ref.get());
            edt(() -> {
                ref.get().showSaved();
                assertTrue(ref.get().visibleText().contains("Completed"));
                assertEquals("prince_ali_rescue", ref.get().selectedGoals().focus);
                preview(ref.get()); ref.get().close();
            });
            // A fresh panel restores without contacting Python at all.
            server.stop(0);
            edt(() -> ref.set(new CompanionPanel(endpoint, new GoalSelectionStore(path))));
            ready(ref.get());
            edt(() -> {
                assertEquals("wear_rune_platebody", ref.get().selectedGoals().focus);
                ref.get().showSaved();
                assertTrue(ref.get().visibleText().contains("Wear a rune platebody"));
                assertTrue(ref.get().visibleText().contains("refresh progress"));
                ref.get().selectDemo(1);
                assertEquals("prince_ali_rescue", ref.get().selectedGoals().focus);
                assertFalse(ref.get().selectedGoals().saved.containsKey("wear_rune_platebody"));
                ref.get().showFocus();
                assertTrue(click(ref.get(), "Unpin focus")); // Offline changes still persist.
            });
            ready(ref.get());
            assertNull(new GoalSelectionStore(path).load().get("partial").focus);
        } finally {
            if (ref.get() != null) edt(() -> ref.get().close());
            server.stop(0);
        }
    }

    @Test public void corruptLoadCanBeRetriedAndFailedWriteKeepsConfirmedChoices() throws Exception {
        Path path = temp.getRoot().toPath().resolve("goals.json");
        Files.writeString(path, "{");
        AtomicReference<CompanionPanel> ref = new AtomicReference<>();
        edt(() -> ref.set(new CompanionPanel(URI.create("http://127.0.0.1:1/v1/goals/evaluate"), new GoalSelectionStore(path))));
        try {
            await(() -> ref.get().selectionText().contains("Could not load saved goals"));
            assertEquals("{", Files.readString(path));
            java.util.Map<String, GoalSelection> state = new java.util.LinkedHashMap<>();
            state.put("fresh", GoalSelection.empty().pin("wear_rune_platebody", "Wear a rune platebody"));
            new GoalSelectionStore(path).save(state);
            edt(() -> assertTrue(click(ref.get(), "Retry loading saved goals")));
            ready(ref.get());
            // Make the write destination unusable after a successful read.
            Files.delete(path); Files.createDirectory(path);
            edt(() -> assertTrue(click(ref.get(), "Unpin focus")));
            await(() -> ref.get().selectionText().contains("Could not save changes"));
            edt(() -> assertEquals("wear_rune_platebody", ref.get().selectedGoals().focus));
            Files.delete(path);
            edt(() -> assertTrue(click(ref.get(), "Unpin focus")));
            ready(ref.get());
            assertNull(new GoalSelectionStore(path).load().get("fresh").focus);
        } finally { edt(() -> ref.get().close()); }
    }

    private static void ready(CompanionPanel panel) throws Exception { await(panel::selectionsReady); }
    private static void connected(CompanionPanel panel) throws Exception { await(() -> "Connected to Python".equals(panel.connectionText())); }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
            edt(() -> done.set(condition.getAsBoolean()));
            if (done.get()) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for panel state");
    }

    private static boolean click(Container container, String label) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof AbstractButton && label.equals(((AbstractButton) child).getText())) {
                ((AbstractButton) child).doClick(); return true;
            }
            if (child instanceof Container && click((Container) child, label)) return true;
        }
        return false;
    }

    private static void preview(CompanionPanel panel) {
        panel.setSize(225, 1100); layout(panel);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(225, 1100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
        try { javax.imageio.ImageIO.write(image, "png", new java.io.File("build/saved-focus-preview.png")); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
    }
}
