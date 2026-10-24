package dev.beginnercompanion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("beginnercompanion")
public interface CompanionConfig extends Config {
    @ConfigItem(keyName = "showFocusOverlay", name = "Show focus overlay",
        description = "Show your pinned live character goal and progress in the game view")
    default boolean showFocusOverlay() { return true; }
}
