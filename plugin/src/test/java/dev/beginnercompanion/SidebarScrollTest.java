package dev.beginnercompanion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.awt.Container;
import java.awt.Point;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class SidebarScrollTest {
    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    @Test public void liveRefreshKeepsScrollAndExpandedDiaryIncludingScrollWhileRequestIsPending() throws Exception {
        AtomicReference<CountDownLatch> gate = new AtomicReference<>(new CountDownLatch(0));
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/goals/evaluate", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            requests.incrementAndGet();
            try { gate.get().await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            JsonObject response = JsonParser.parseReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/fixtures/goals-partial.response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            response.add("session_id", request.get("session_id")); response.add("revision", request.get("revision"));
            byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
            try { exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
        AtomicReference<Panel> ref = new AtomicReference<>();
        try {
            edt(() -> {
                ref.set(new Panel(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/goals/evaluate")));
                ref.get().getWrappedPanel().setSize(242, 400);
                layout(ref.get().getWrappedPanel());
                ref.get().enableLiveMode(); ref.get().setLiveSnapshot(snapshot(1));
            });
            connected(ref.get());
            edt(() -> {
                ref.get().selectGoal("ardougne_cloak_1");
                assertTrue(click(ref.get(), "Show diary tasks"));
                layout(ref.get().getWrappedPanel());
                ref.get().scroll().getViewport().setViewPosition(new Point(0, 420));
                assertEquals(420, ref.get().scroll().getViewport().getViewPosition().y);
                gate.set(new CountDownLatch(1));
                ref.get().setLiveSnapshot(snapshot(2));
                assertTrue(ref.get().visibleText().contains("Check insured pets"));
                assertEquals(420, ref.get().scroll().getViewport().getViewPosition().y);
                // A user can keep scrolling during network latency; completion must
                // preserve the NEW position, not a position captured at request start.
                ref.get().scroll().getViewport().setViewPosition(new Point(0, 600));
            });
            await(() -> requests.get() == 2);
            edt(() -> assertEquals("While response is blocked", 600, ref.get().scroll().getViewport().getViewPosition().y));
            gate.get().countDown(); connected(ref.get());
            edt(() -> {
                assertEquals(600, ref.get().scroll().getViewport().getViewPosition().y);
                assertTrue(ref.get().visibleText().contains("Check insured pets"));
            });
            // Let deferred Swing validation run, then check it did not reset later.
            edt(() -> layout(ref.get().getWrappedPanel()));
            edt(() -> {
                assertEquals(600, ref.get().scroll().getViewport().getViewPosition().y);
                ref.get().selectGoal("wear_rune_platebody");
                assertEquals(0, ref.get().scroll().getViewport().getViewPosition().y);
                ref.get().setLiveSnapshot(null);
                assertTrue(ref.get().visibleText().contains("Log in to load your character"));
                assertFalse(ref.get().visibleText().contains("Check insured pets"));
            });
        } finally {
            gate.get().countDown(); if (ref.get() != null) edt(() -> ref.get().close()); server.stop(0);
        }
    }

    private static LiveSnapshotReader.Snapshot snapshot(int qp) {
        JsonObject account = new JsonObject(); account.addProperty("quest_points", qp);
        return new LiveSnapshotReader.Snapshot("live:scroll-test", "Synthetic Player", account);
    }
    private static class Panel extends CompanionPanel {
        Panel(URI endpoint) { super(new PlannerTransport(new okhttp3.OkHttpClient()), new com.google.gson.Gson(),
            ServiceSettings.development("http://" + endpoint.getRawAuthority()), GoalSelectionStore.inMemory()); }
        JScrollPane scroll() { return getScrollPane(); }
    }
    private static boolean click(Container parent, String title) {
        for (java.awt.Component child : parent.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof javax.swing.AbstractButton && title.equals(((javax.swing.AbstractButton) child).getText())) {
                ((javax.swing.AbstractButton) child).doClick(); return true;
            }
            if (child instanceof Container && click((Container) child, title)) return true;
        }
        return false;
    }
    private static void layout(Container parent) {
        parent.doLayout();
        for (java.awt.Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child);
    }
    private static void connected(CompanionPanel panel) throws Exception {
        await(() -> {
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            try { edt(() -> ready.set("Connected to Python".equals(panel.connectionText()))); }
            catch (Exception e) { throw new RuntimeException(e); }
            return ready.get();
        });
    }
    private static void await(java.util.function.BooleanSupplier predicate) throws Exception {
        for (int i = 0; i < 100; i++) { if (predicate.getAsBoolean()) return; Thread.sleep(50); }
        fail("Timed out waiting for refresh");
    }
}
