package dev.beginnercompanion;

import com.google.gson.Gson;
import dev.beginnercompanion.bridge.PlannerClient;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import org.junit.Test;
import static org.junit.Assert.*;

public class FocusOverlayTest {
    @Test public void chosenTaskReplacesOptionsAndTracksCompletionWithoutChangingFocus() {
        GoalSelection selected = GoalSelection.empty().pin("wear_rune_platebody", "Rune platebody")
            .task("defence_40", "Work towards 40 Defence");
        PlannerClient.GoalResult partial = goal("partial", "wear_rune_platebody");
        FocusOverlayState state = FocusOverlayState.from("local", "Rune platebody", partial, "", selected);
        assertEquals("Wear a rune platebody", state.title);
        assertEquals("Current task: Work towards 40 Defence", state.checks.get(0));
        assertEquals("Task: In progress", state.checks.get(1));
        FocusOverlayState ready = FocusOverlayState.from("local", "Rune platebody", goal("unclaimed", "wear_rune_platebody"), "", selected);
        assertEquals("Task: Completed", ready.checks.get(1));
        assertEquals("wear_rune_platebody", selected.focus);
        assertEquals("defence_40", selected.taskId);
        FocusOverlayState updating = FocusOverlayState.from("local", "Rune platebody", null, "Updating progress...", selected);
        assertEquals("Task: Updating progress...", updating.checks.get(1));
        assertTrue(updating.progress.isEmpty());
        GoalSelection unavailable = selected.task("old_task", "Old activity");
        assertEquals("Review task: no longer suggested", CurrentTask.status(unavailable, partial));
        GoalSelection check = selected.task("defence_40", "Check: Reach 40 Defence");
        assertEquals("Needs an in-game check", CurrentTask.status(check, goal("unknown", "wear_rune_platebody")));
    }

    @Test public void progressIsCopiedAndUnavailableStatesDoNotRetainOldCounts() throws Exception {
        PlannerClient.GoalResult goal = goal("partial", "wear_rune_platebody");
        FocusOverlayState view = FocusOverlayState.from("local", "cached", goal, "Updating progress...");
        assertEquals("Wear a rune platebody", view.title);
        assertEquals("0 / 3 milestones met", view.progress);
        assertEquals(2, view.checks.size());
        assertEquals("Option: Work towards 40 Defence", view.checks.get(0));
        goal.progress.satisfied = 99;
        goal.requirements[0].label = "Mutated later";
        assertEquals("0 / 3 milestones met", view.progress);
        assertFalse(view.checks.get(0).contains("Mutated later"));
        assertThrows(UnsupportedOperationException.class, () -> view.checks.add("unsafe"));
        FocusOverlayState offline = FocusOverlayState.from("local", view.title, null, "Python unavailable");
        assertEquals("Python unavailable", offline.status);
        assertTrue(offline.progress.isEmpty());
        assertTrue(offline.checks.isEmpty());
        FocusOverlayState completed = FocusOverlayState.from("local", "cached", goal("complete", "wear_rune_platebody"), "");
        assertEquals("Completed", completed.status);
        assertTrue(completed.checks.isEmpty());
        FocusOverlayState unknown = FocusOverlayState.from("local", "cached", goal("unknown", "wear_rune_platebody"), "");
        assertTrue(unknown.progress.contains("3 unknown"));
        assertTrue(unknown.checks.get(0).startsWith("Check:"));
    }

    @Test public void rendersCompactMovableOverlayAndHidesOnLogoutOtherCharacterOrSetting() throws Exception {
        FakeClient game = new FakeClient();
        CompanionConfig visible = new CompanionConfig() {};
        FocusOverlay overlay = new FocusOverlay(null, game.client(), visible);
        assertTrue(overlay.isMovable());
        assertFalse(overlay.isResizable());
        BufferedImage image = new BufferedImage(320, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setFont(new Font("Dialog", Font.PLAIN, 12));
        try {
            assertNull(overlay.render(graphics));
            FocusOverlayState view = FocusOverlayState.from(LiveSnapshotReader.localKey(42), "cached",
                goal("partial", "wear_rune_platebody"), "");
            overlay.update(view);
            overlay.render(graphics); // RuneLite panels cache child dimensions for the next frame.
            graphics.setComposite(java.awt.AlphaComposite.Clear); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            Dimension bounds = overlay.render(graphics);
            assertNotNull(bounds);
            javax.imageio.ImageIO.write(image, "png", new java.io.File("build/focus-overlay-preview.png"));
            assertTrue(bounds.toString(), bounds.width <= 260);
            assertTrue(bounds.toString(), bounds.height > 70 && bounds.height < 250);
            game.state = GameState.LOGIN_SCREEN;
            assertNull(overlay.render(graphics));
            game.state = GameState.LOGGED_IN; game.hash = 43;
            assertNull(overlay.render(graphics));
            game.hash = 42;
            overlay.update(FocusOverlayState.HIDDEN);
            assertNull(overlay.render(graphics));
            FocusOverlay hidden = new FocusOverlay(null, game.client(), new CompanionConfig() {
                @Override public boolean showFocusOverlay() { return false; }
            });
            hidden.update(view);
            assertNull(hidden.render(graphics));
        } finally { graphics.dispose(); }
    }

    private PlannerClient.GoalResult goal(String fixture, String id) {
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
            "/fixtures/goals-" + fixture + ".response.json"), StandardCharsets.UTF_8)) {
            PlannerClient.GoalsResult result = new Gson().fromJson(reader, PlannerClient.GoalsResult.class);
            for (PlannerClient.GoalResult goal : result.goals) if (goal.goal.id.equals(id)) return goal;
            throw new AssertionError("Missing fixture goal");
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    private static final class FakeClient {
        GameState state = GameState.LOGGED_IN;
        long hash = 42;
        Client client() {
            return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class[]{Client.class}, (object, method, args) -> {
                if ("getGameState".equals(method.getName())) return state;
                if ("getAccountHash".equals(method.getName())) return hash;
                throw new AssertionError("Unexpected rendering access: " + method.getName());
            });
        }
    }
}
