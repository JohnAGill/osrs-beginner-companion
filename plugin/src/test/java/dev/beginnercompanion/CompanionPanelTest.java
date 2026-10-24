package dev.beginnercompanion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompanionPanelTest {
    private static void edt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }

    @Test public void displaysPythonRowsAndDiscardsSelectionChanges() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/v1/goals/evaluate", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            received.countDown();
            try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            String fixture = request.getAsJsonObject("account").size() == 0 ? "unknown" : "fresh";
            JsonObject response = JsonParser.parseReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/fixtures/goals-" + fixture + ".response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            response.add("session_id", request.get("session_id"));
            response.add("revision", request.get("revision"));
            byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
        AtomicReference<CompanionPanel> ref = new AtomicReference<>();
        try {
            edt(() -> { ref.set(new CompanionPanel(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/goals/evaluate"))); ref.get().evaluate(); });
            assertTrue(received.await(3, TimeUnit.SECONDS));
            edt(() -> assertEquals("Checking Python...", ref.get().connectionText()));
            // Changing the selection invalidates the outstanding snapshot immediately.
            edt(() -> ref.get().selectDemo(1));
            release.countDown();
            edt(() -> { assertEquals("Not checked", ref.get().connectionText()); assertFalse(ref.get().visibleText().contains("Wear a rune platebody")); });
            // A new request can succeed after cancellation of the previous one.
            edt(() -> { ref.get().selectDemo(0); ref.get().evaluate(); });
            awaitStatus(ref.get(), "Connected to Python");
            edt(() -> {
                ref.get().showBrowser();
                assertTrue(ref.get().resultCount() > 0);
                assertTrue(ref.get().visibleText().contains("Wear a rune platebody"));
                assertTrue(ref.get().visibleText().contains("Unlock Ardougne cloak 1"));
                assertTrue(ref.get().visibleText().contains("Complete Prince Ali Rescue"));
                assertTrue(ref.get().visibleText().contains("Reach 30 Mining for coal"));
                ref.get().searchGoals("teleport", 1);
                assertTrue(ref.get().visibleText().contains("Reach 25 Magic for Varrock Teleport"));
                assertFalse(ref.get().visibleText().contains("Wear a rune platebody"));
                ref.get().searchGoals("", 2);
                assertTrue(ref.get().visibleText().contains("Complete Plague City"));
                assertFalse(ref.get().visibleText().contains("Complete Prince Ali Rescue"));
                ref.get().searchGoals("no-such-goal", 0);
                assertTrue(ref.get().visibleText().contains("No matching goals"));
                ref.get().searchGoals("", 0);
                ref.get().selectGoal("prince_ali_rescue");
                assertFalse(ref.get().visibleText().contains("10-coin fee"));
                assertTrue(click(ref.get(), "▸ Benefits and investment"));
                assertTrue(ref.get().visibleText().contains("10-coin fee"));
                CompanionPanel panel = ref.get();
                panel.setSize(225, 650);
                layout(panel);
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(225, 650, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D graphics = image.createGraphics();
                panel.printAll(graphics); graphics.dispose();
                try { javax.imageio.ImageIO.write(image, "png", new java.io.File("build/goal-detail-preview.png")); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
                ref.get().showBrowser();
                assertTrue(ref.get().visibleText().contains("Explore goals"));
                layout(panel);
                java.awt.Graphics2D browserGraphics = image.createGraphics();
                panel.printAll(browserGraphics); browserGraphics.dispose();
                try { javax.imageio.ImageIO.write(image, "png", new java.io.File("build/goal-browser-preview.png")); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
                ref.get().selectGoal("ardougne_cloak_1");
                assertTrue(ref.get().visibleText().contains("Claim Ardougne cloak 1"));
                assertTrue(ref.get().visibleText().contains("Access requirement"));
                assertFalse(ref.get().visibleText().contains("Check insured pets"));
                assertTrue(click(ref.get(), "Show diary tasks"));
                assertTrue(ref.get().visibleText().contains("Check insured pets"));
                ref.get().selectGoal("wear_rune_platebody");
                assertTrue(ref.get().visibleText().contains("Still needed — Reach 40 Defence"));
                ref.get().selectDemo(4);
                ref.get().evaluate();
            });
            awaitStatus(ref.get(), "Connected to Python");
            edt(() -> {
                assertTrue(ref.get().visibleText().contains("Completion unknown"));
                assertTrue(ref.get().visibleText().contains("0 / 3 milestones met; 3 unknown"));
                ref.get().selectGoal("ardougne_cloak_1");
                assertTrue(ref.get().visibleText().contains("Unknown — Claim Ardougne cloak 1"));
            });
        } finally {
            release.countDown();
            if (ref.get() != null) edt(() -> ref.get().close());
            server.stop(0);
        }
    }

    @Test public void unavailableServiceAllowsRetryAndCloseIgnoresCompletion() throws Exception {
        java.net.ServerSocket socket = new java.net.ServerSocket(0);
        int port = socket.getLocalPort(); socket.close();
        AtomicReference<CompanionPanel> ref = new AtomicReference<>();
        edt(() -> { ref.set(new CompanionPanel(URI.create("http://127.0.0.1:" + port + "/v1/goals/evaluate"))); ref.get().evaluate(); });
        try {
            awaitStatus(ref.get(), "Service unavailable");
            edt(() -> ref.get().evaluate());
            awaitStatus(ref.get(), "Service unavailable");
        } finally { edt(() -> ref.get().close()); }
        edt(() -> { ref.get().evaluate(); assertEquals("Not checked", ref.get().connectionText()); });
    }

    @Test public void preferenceAnswersReachPythonAndResetClearsPersonalisation() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<JsonObject> sent = new AtomicReference<>();
        server.createContext("/v1/goals/evaluate", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            sent.set(request);
            received.countDown();
            try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            boolean answered = request.getAsJsonObject("preferences").getAsJsonArray("interests").size() > 0;
            String fixture = answered ? "preferences-combat" : "goals-partial";
            JsonObject response = JsonParser.parseReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/fixtures/" + fixture + ".response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            response.add("session_id", request.get("session_id"));
            response.add("revision", request.get("revision"));
            byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
        AtomicReference<CompanionPanel> ref = new AtomicReference<>();
        try {
            edt(() -> {
                ref.set(new CompanionPanel(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/goals/evaluate")));
                ref.get().selectDemo(1);
                ref.get().setPreferences(new String[]{"combat"}, 3);
                ref.get().evaluate();
            });
            assertTrue(received.await(3, TimeUnit.SECONDS));
            assertEquals("long", sent.get().getAsJsonObject("preferences").get("commitment").getAsString());
            assertEquals("combat", sent.get().getAsJsonObject("preferences").getAsJsonArray("interests").get(0).getAsString());
            // Editing an answer during a pending request invalidates that response.
            edt(() -> ref.get().setPreferences(new String[]{"exploration"}, 2));
            release.countDown();
            edt(() -> assertEquals("Not checked", ref.get().connectionText()));
            edt(() -> { ref.get().setPreferences(new String[]{"combat"}, 3); ref.get().evaluate(); });
            awaitStatus(ref.get(), "Connected to Python");
            edt(() -> {
                ref.get().showBrowser();
                assertTrue(ref.get().visibleText().contains("Suggested for you"));
                assertFalse(ref.get().visibleText().contains("Matches your interest in combat"));
                ref.get().selectGoal("wear_rune_platebody");
                assertTrue(click(ref.get(), "▸ Why this goal fits"));
                assertTrue(ref.get().visibleText().contains("Matches your interest in combat"));
                ref.get().showBrowser();
                assertTrue(click(ref.get(), "▸ Settings"));
                click(ref.get(), "Your preferences (optional)");
                ref.get().setSize(225, 1400);
                layout(ref.get());
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(225, 1400, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D graphics = image.createGraphics();
                ref.get().printAll(graphics); graphics.dispose();
                try { javax.imageio.ImageIO.write(image, "png", new java.io.File("build/preferences-preview.png")); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
                click(ref.get(), "Show a mixture");
                assertEquals("Not checked", ref.get().connectionText());
                ref.get().evaluate();
            });
            awaitStatus(ref.get(), "Connected to Python");
            edt(() -> {
                assertTrue(ref.get().visibleText().contains("A mix to explore"));
                assertFalse(ref.get().visibleText().contains("Best fit for your answers"));
            });
            assertEquals(0, sent.get().getAsJsonObject("preferences").getAsJsonArray("interests").size());
            assertEquals("any", sent.get().getAsJsonObject("preferences").get("commitment").getAsString());
        } finally {
            release.countDown();
            if (ref.get() != null) edt(() -> ref.get().close());
            server.stop(0);
        }
    }

    private static boolean click(java.awt.Container container, String label) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.AbstractButton && label.equals(((javax.swing.AbstractButton) child).getText())) {
                ((javax.swing.AbstractButton) child).doClick(); return true;
            }
            if (child instanceof java.awt.Container && click((java.awt.Container) child, label)) return true;
        }
        return false;
    }

    private static void awaitStatus(CompanionPanel panel, String expected) throws Exception {
        for (int i = 0; i < 100; i++) {
            AtomicReference<String> status = new AtomicReference<>();
            edt(() -> status.set(panel.connectionText()));
            if (expected.equals(status.get())) return;
            Thread.sleep(50);
        }
        fail("Panel never reached: " + expected);
    }

    private static void layout(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container) layout((java.awt.Container) child);
        }
    }
}
