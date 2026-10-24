package dev.beginnercompanion;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

final class FocusOverlay extends OverlayPanel {
    private static final Color GOLD = new Color(239, 184, 70);
    private final Client client;
    private final CompanionConfig config;
    private volatile FocusOverlayState state = FocusOverlayState.HIDDEN;

    FocusOverlay(CompanionPlugin plugin, Client client, CompanionConfig config) {
        super(plugin);
        this.client = client; this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setResizable(false);
        panelComponent.setPreferredSize(new Dimension(240, 0));
    }

    void update(FocusOverlayState state) { this.state = state; }

    @Override public String getName() { return "Beginner Companion Focus"; }

    @Override public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        FocusOverlayState view = state;
        if (!config.showFocusOverlay() || view.localKey == null || client.getGameState() != GameState.LOGGED_IN) return null;
        // Guard independently of Swing callbacks, including the frame of a character switch.
        if (!view.localKey.equals(LiveSnapshotReader.localKey(client.getAccountHash()))) return null;
        line("Current focus", GOLD);
        line(view.title, Color.WHITE);
        line(view.status, "Completed".equals(view.status) ? new Color(100, 220, 130) : GOLD);
        if (!view.progress.isEmpty()) line(view.progress, Color.LIGHT_GRAY);
        for (String check : view.checks) line(check, Color.LIGHT_GRAY);
        return super.render(graphics);
    }

    private void line(String text, Color color) {
        panelComponent.getChildren().add(LineComponent.builder().left(text).leftColor(color).build());
    }
}
