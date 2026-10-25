package dev.beginnercompanion;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class HostedPlannerTest {
    @Test public void hostedSettingsRequireExplicitConsentAndAnHttpsOrigin() {
        assertFalse(ServiceSettings.hosted("", true).canSend());
        assertFalse(ServiceSettings.hosted("https://planner.example", false).canSend());
        assertTrue(ServiceSettings.hosted("https://planner.example/", true).canSend());
        for (String url : new String[]{"http://planner.example", "https://user:pass" + '@' + "planner.example",
            "https://planner.example/path", "https://planner.example?token=x", "https://planner.example/#x",
            "https://planner.example:0", "https://planner.example:99999"})
            assertFalse(url, ServiceSettings.hosted(url, true).canSend());
        assertTrue(ServiceSettings.development("http://127.0.0.1:8765").canSend());
        assertFalse(ServiceSettings.development("http://planner.example").canSend());
        assertTrue(ServiceSettings.hosted("https://planner.example", true).disclosure().contains("IP address"));
    }

    @Test public void noRequestsBeforeOptInAndRevokingConsentCancelsPendingCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<String> network = new CompletableFuture<>();
        AtomicReference<CompanionPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new CompanionPanel((url, body) -> { calls.incrementAndGet(); return network; },
                new Gson(), ServiceSettings.hosted("https://planner.example", false), GoalSelectionStore.inMemory()));
            panel.get().evaluate();
            assertEquals(0, calls.get());
            assertEquals("Planner disabled", panel.get().connectionText());
            panel.get().configureService(ServiceSettings.hosted("https://planner.example", true));
            panel.get().evaluate();
            assertEquals(1, calls.get());
            panel.get().configureService(ServiceSettings.hosted("https://planner.example", false));
            assertTrue(network.isCancelled());
            panel.get().evaluate();
            assertEquals(1, calls.get());
            panel.get().close();
        });
    }

    @Test public void transportRejectsRedirectsWithoutForwardingObservations() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger forwarded = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/other");
            exchange.sendResponseHeaders(307, -1); exchange.close();
        });
        server.createContext("/other", exchange -> { forwarded.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        try {
            PlannerTransport transport = new PlannerTransport(new OkHttpClient());
            Exception error = assertThrows(Exception.class, () -> transport.post(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect"), "private-observations").get(3, TimeUnit.SECONDS));
            assertTrue(error.getCause().getMessage().contains("307"));
            assertEquals(0, forwarded.get());
        } finally { server.stop(0); }
    }

    @Test public void hostedLiveUpdatesAreCoalescedDuringMinimumRefreshInterval() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<CompanionPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new CompanionPanel((url, body) -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(reply(body)); },
                new Gson(), ServiceSettings.hosted("https://planner.example", true), GoalSelectionStore.inMemory()));
            panel.get().enableLiveMode();
            com.google.gson.JsonObject account = new com.google.gson.JsonObject(); account.addProperty("quest_points", 0);
            panel.get().setLiveSnapshot(new LiveSnapshotReader.Snapshot("live:test", "Synthetic Player", account));
        });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(1, calls.get());
            com.google.gson.JsonObject account = new com.google.gson.JsonObject(); account.addProperty("quest_points", 1);
            panel.get().setLiveSnapshot(new LiveSnapshotReader.Snapshot("live:test", "Synthetic Player", account));
            panel.get().evaluate();
            assertEquals(1, calls.get());
            panel.get().close();
        });
    }

    @Test public void anOutageKeepsLastConfirmedResultsButMarksThemStale() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<CompanionPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new CompanionPanel((url, body) -> calls.incrementAndGet() == 1
                ? CompletableFuture.completedFuture(reply(body))
                : CompletableFuture.failedFuture(new java.io.IOException("Unavailable")),
                new Gson(), ServiceSettings.development("http://127.0.0.1:8765"), GoalSelectionStore.inMemory()));
            panel.get().evaluate();
        });
        SwingUtilities.invokeAndWait(() -> { assertEquals("Connected to Python", panel.get().connectionText()); panel.get().evaluate(); });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("Service unavailable", panel.get().connectionText());
            panel.get().showBrowser();
            assertTrue(panel.get().visibleText().contains("Wear a rune platebody"));
            assertTrue(panel.get().visibleText().contains("out of date"));
            panel.get().close();
        });
    }

    private static String reply(String body) {
        com.google.gson.JsonObject request = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        com.google.gson.JsonObject response = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
            HostedPlannerTest.class.getResourceAsStream("/fixtures/goals-fresh.response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        response.add("session_id", request.get("session_id")); response.add("revision", request.get("revision"));
        return response.toString();
    }

    @Test public void transportBoundsChunkedResponsesAndRejectsNonJson() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try { exchange.getResponseBody().write(new byte[(int) PlannerTransport.MAX_RESPONSE_BYTES + 1]); }
            catch (java.io.IOException cancelled) { /* Expected when the client stops reading. */ }
            finally { exchange.close(); }
        });
        server.createContext("/html", exchange -> {
            byte[] bytes = "<html>not a planner response</html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            PlannerTransport transport = new PlannerTransport(new OkHttpClient());
            for (String path : new String[]{"/large", "/html"})
                assertThrows(Exception.class, () -> transport.post(URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + path), "{}").get(5, TimeUnit.SECONDS));
        } finally { server.stop(0); }
    }
}
