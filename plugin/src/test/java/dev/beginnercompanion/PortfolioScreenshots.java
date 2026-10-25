package dev.beginnercompanion;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.laf.RuneLiteLAF;
import dev.beginnercompanion.bridge.PlannerClient;

/** Reproducible real UI captures with fictional fixtures. Never starts the game or reads user state. */
public final class PortfolioScreenshots {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "../docs/images" : args[0]).toAbsolutePath();
        Files.createDirectories(output);
        RuneLiteLAF.setup();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/goals/evaluate", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject response = fixture();
            response.add("session_id", request.get("session_id")); response.add("revision", request.get("revision"));
            byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
        Path temp = Files.createTempDirectory("companion-screenshots");
        Path state = temp.resolve("synthetic-goals.json");
        Map<String, GoalSelection> choices = new LinkedHashMap<>();
        choices.put("partial", GoalSelection.empty().pin("wear_rune_platebody", "Wear a rune platebody")
            .task("defence_40", "Work towards 40 Defence"));
        new GoalSelectionStore(state).save(choices);
        AtomicReference<CompanionPanel> ref = new AtomicReference<>();
        try {
            edt(() -> {
                ref.set(new CompanionPanel(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/goals/evaluate"), new GoalSelectionStore(state)));
                ref.get().selectDemo(1); ref.get().setPreferences(new String[]{"combat"}, 3); ref.get().evaluate();
            });
            boolean loaded = false;
            for (int i = 0; i < 200; i++) {
                AtomicBoolean ready = new AtomicBoolean();
                edt(() -> ready.set(ref.get().selectionsReady() && "Connected to Python".equals(ref.get().connectionText())));
                if (ready.get()) { loaded = true; break; }
                Thread.sleep(25);
            }
            if (!loaded) throw new IllegalStateException("Demo panel did not load");
            edt(() -> {
                try {
                    CompanionPanel panel = ref.get();
                    panel.showFocus(); capture(panel, output.resolve("focus.png"), 520);
                    panel.showBrowser(); panel.searchGoals("travel", 1); capture(panel, output.resolve("explore.png"), 660);
                    panel.searchGoals("", 0); panel.selectGoal("wear_rune_platebody"); capture(panel, output.resolve("goal-detail.png"), 760);
                    captureOverlay(output.resolve("overlay.png"));
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
        } finally {
            if (ref.get() != null) edt(() -> ref.get().close());
            server.stop(0);
            // The serial storage worker may still be finishing the final baseline write.
            for (int i = 0; i < 40; i++) {
                try { Files.deleteIfExists(state); Files.deleteIfExists(temp); break; }
                catch (java.io.IOException busy) { if (i == 39) throw busy; Thread.sleep(25); }
            }
        }
        System.out.println("Synthetic portfolio screenshots written to " + output);
    }

    private static JsonObject fixture() {
        return JsonParser.parseReader(new java.io.InputStreamReader(PortfolioScreenshots.class.getResourceAsStream(
            "/fixtures/preferences-combat.response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child);
    }
    private static void capture(CompanionPanel panel, Path file, int height) throws Exception {
        JPanel wrapped = panel.getWrappedPanel();
        wrapped.setSize(242, height); layout(wrapped);
        BufferedImage image = new BufferedImage(484, height * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); graphics.scale(2, 2);
        wrapped.printAll(graphics); graphics.dispose(); ImageIO.write(image, "png", file.toFile());
    }
    private static void captureOverlay(Path file) throws Exception {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class[]{Client.class}, (proxy, method, args) -> {
            if (method.getName().equals("getGameState")) return GameState.LOGGED_IN;
            if (method.getName().equals("getAccountHash")) return 42L;
            throw new UnsupportedOperationException(method.getName());
        });
        PlannerClient.GoalsResult results = new Gson().fromJson(fixture(), PlannerClient.GoalsResult.class);
        PlannerClient.GoalResult goal = Arrays.stream(results.goals).filter(g -> g.goal.id.equals("wear_rune_platebody")).findFirst().orElseThrow();
        GoalSelection choice = GoalSelection.empty().pin(goal.goal.id, goal.goal.title).task("defence_40", "Work towards 40 Defence");
        FocusOverlay overlay = new FocusOverlay(null, client, new CompanionConfig() {});
        overlay.update(FocusOverlayState.from(LiveSnapshotReader.localKey(42), goal.goal.title, goal, "", choice));
        BufferedImage image = new BufferedImage(520, 360, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics(); graphics.scale(2, 2);
        graphics.setFont(new Font("Dialog", Font.PLAIN, 12));
        overlay.render(graphics); graphics.setComposite(AlphaComposite.Clear); graphics.fillRect(0, 0, 260, 180);
        graphics.setComposite(AlphaComposite.SrcOver); overlay.render(graphics); graphics.dispose();
        ImageIO.write(image, "png", file.toFile());
    }
}
