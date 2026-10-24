package dev.beginnercompanion;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "Beginner Companion", description = "Explore beginner goals with live character progress or demo accounts", tags = {"goals", "beginner", "demo"})
public class CompanionPlugin extends Plugin {
    @Inject private ClientToolbar toolbar;
    @Inject private Client client;
    @Inject private CompanionConfig config;
    @Inject private OverlayManager overlayManager;
    private FocusOverlay focusOverlay;
    private final LiveSnapshotReader liveReader = new LiveSnapshotReader();
    private boolean dirty = true;
    private int ticks;
    private NavigationButton navigation;
    private volatile CompanionPanel panel;

    @Provides CompanionConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(CompanionConfig.class);
    }

    @Override protected void startUp() throws Exception {
        focusOverlay = new FocusOverlay(this, client, config);
        onEdt(() -> {
            panel = new CompanionPanel();
            panel.setFocusListener(focusOverlay::update);
            panel.enableLiveMode();
            BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = icon.createGraphics();
            g.setColor(new Color(239, 184, 70));
            g.fillOval(1, 1, 14, 14);
            g.setColor(new Color(40, 40, 40));
            g.drawString("G", 4, 12);
            g.dispose();
            navigation = NavigationButton.builder().tooltip("Beginner Companion").icon(icon)
                .priority(8).panel(panel).build();
            toolbar.addNavigation(navigation);
        });
        overlayManager.add(focusOverlay);
    }

    @Subscribe public void onStatChanged(StatChanged event) { dirty = true; }
    @Subscribe public void onVarbitChanged(VarbitChanged event) { dirty = true; }
    @Subscribe public void onItemContainerChanged(ItemContainerChanged event) { dirty = true; }
    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        dirty = true; ticks = 0;
        if (event.getGameState() != GameState.LOGGED_IN) {
            CompanionPanel target = panel;
            if (target != null) SwingUtilities.invokeLater(() -> target.setLiveSnapshot(null));
        }
    }
    @Subscribe public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        ticks++;
        if (ticks < 2 || (!dirty && ticks % 10 != 0)) return;
        dirty = false;
        LiveSnapshotReader.Snapshot snapshot = liveReader.read(client);
        CompanionPanel target = panel;
        if (target != null) SwingUtilities.invokeLater(() -> target.setLiveSnapshot(snapshot));
    }

    @Override protected void shutDown() throws Exception {
        if (focusOverlay != null) {
            focusOverlay.update(FocusOverlayState.HIDDEN);
            overlayManager.remove(focusOverlay);
        }
        onEdt(() -> {
            if (panel != null) panel.close();
            if (navigation != null) toolbar.removeNavigation(navigation);
            panel = null;
            navigation = null;
        });
        focusOverlay = null;
    }

    private static void onEdt(Runnable action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeAndWait(action);
    }
}
