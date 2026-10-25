package dev.beginnercompanion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class LivePanelTest {
    @org.junit.Rule public org.junit.rules.TemporaryFolder temp = new org.junit.rules.TemporaryFolder();
    private static void edt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }

    @Test public void coalescesLiveUpdatesAndClearsOnLogoutWithoutSendingIdentity() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<CountDownLatch> gate = new AtomicReference<>(new CountDownLatch(1));
        AtomicReference<JsonObject> sent = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/goals/evaluate", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            sent.set(request); calls.incrementAndGet();
            try { gate.get().await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            JsonObject response = JsonParser.parseReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/fixtures/goals-fresh.response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            response.add("session_id", request.get("session_id")); response.add("revision", request.get("revision"));
            byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
            try { exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
        AtomicReference<CompanionPanel> panel = new AtomicReference<>();
        AtomicReference<FocusOverlayState> focus = new AtomicReference<>(FocusOverlayState.HIDDEN);
        GoalSelectionStore store = TestPanels.store(temp.getRoot().toPath().resolve("goals.json"));
        java.util.Map<String, GoalSelection> choices = new java.util.LinkedHashMap<>();
        choices.put("live:first", GoalSelection.empty().pin("wear_rune_platebody", "Wear a rune platebody"));
        store.save(choices);
        try {
            edt(() -> {
                panel.set(TestPanels.create(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/goals/evaluate"), store));
                panel.get().setFocusListener(focus::set);
                panel.get().enableLiveMode();
                panel.get().setLiveSnapshot(snapshot("live:first", 0));
            });
            await(() -> calls.get() == 1);
            awaitEdt(() -> panel.get().selectionsReady());
            assertEquals("Updating progress...", focus.get().status);
            assertTrue(focus.get().progress.isEmpty());
            String firstSession = sent.get().get("session_id").getAsString();
            edt(() -> { panel.get().setLiveSnapshot(snapshot("live:first", 1)); panel.get().setLiveSnapshot(snapshot("live:first", 2)); });
            gate.get().countDown();
            await(() -> calls.get() == 2);
            awaitEdt(() -> "Connected to Python".equals(panel.get().connectionText()));
            assertEquals("0 / 3 milestones met", focus.get().progress);
            assertEquals(2, sent.get().getAsJsonObject("account").get("quest_points").getAsInt());
            assertEquals("live", sent.get().get("source").getAsString());
            assertFalse(sent.get().toString().contains("Synthetic Player"));
            assertFalse(sent.get().toString().contains("live:first"));
            // Same data is not sent on every periodic game tick.
            edt(() -> panel.get().setLiveSnapshot(snapshot("live:first", 2)));
            assertEquals(2, calls.get());
            gate.set(new CountDownLatch(1));
            edt(() -> panel.get().setLiveSnapshot(snapshot("live:first", 3)));
            await(() -> calls.get() == 3);
            edt(() -> panel.get().setLiveSnapshot(null));
            assertSame(FocusOverlayState.HIDDEN, focus.get());
            gate.get().countDown();
            edt(() -> {
                assertEquals("Not checked", panel.get().connectionText());
                assertTrue(panel.get().visibleText().contains("Log in to load your character"));
                assertTrue(panel.get().selectedGoals().saved.isEmpty());
                panel.get().setLiveSnapshot(snapshot("live:second", 10));
            });
            await(() -> calls.get() == 4);
            awaitEdt(() -> "Connected to Python".equals(panel.get().connectionText()));
            assertSame(FocusOverlayState.HIDDEN, focus.get()); // Another character has no pinned goal.
            assertNotEquals(firstSession, sent.get().get("session_id").getAsString());
            assertEquals(10, sent.get().getAsJsonObject("account").get("quest_points").getAsInt());
            edt(() -> { panel.get().selectGoal("wear_rune_platebody"); assertTrue(click(panel.get(), "Pin as focus")); });
            awaitEdt(() -> panel.get().selectionsReady());
            assertEquals("live:second", focus.get().localKey);
            edt(() -> assertTrue(click(panel.get(), "Unpin focus")));
            awaitEdt(() -> panel.get().selectionsReady());
            assertSame(FocusOverlayState.HIDDEN, focus.get());
            edt(() -> { panel.get().selectDemo(0); panel.get().setLiveSnapshot(snapshot("live:second", 11)); });
            assertEquals(4, calls.get()); // Demo mode never auto-submits live observations.
        } finally {
            gate.get().countDown();
            if (panel.get() != null) edt(() -> panel.get().close());
            server.stop(0);
        }
    }

    private static LiveSnapshotReader.Snapshot snapshot(String key, int qp) {
        JsonObject account = new JsonObject(); account.addProperty("quest_points", qp);
        return new LiveSnapshotReader.Snapshot(key, "Synthetic Player", account);
    }
    private static boolean click(java.awt.Container container, String label) {
        for (java.awt.Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof javax.swing.AbstractButton && label.equals(((javax.swing.AbstractButton) child).getText())) {
                ((javax.swing.AbstractButton) child).doClick(); return true;
            }
            if (child instanceof java.awt.Container && click((java.awt.Container) child, label)) return true;
        }
        return false;
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) { if (condition.getAsBoolean()) return; Thread.sleep(50); }
        fail("Timed out waiting for HTTP state");
    }
    private static void awaitEdt(java.util.function.BooleanSupplier condition) throws Exception {
        await(() -> {
            java.util.concurrent.atomic.AtomicBoolean value = new java.util.concurrent.atomic.AtomicBoolean();
            try { edt(() -> value.set(condition.getAsBoolean())); } catch (Exception e) { throw new RuntimeException(e); }
            return value.get();
        });
    }
}
