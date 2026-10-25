package dev.beginnercompanion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("beginnercompanion")
public interface CompanionConfig extends Config {
    @ConfigItem(keyName = "shareProgress", name = "Share progress with planner",
        description = "Send skills/XP, quest/diary/unlock observations, membership, preferences, completion history and feedback to the configured service. No names, account hashes or credentials. Service receives your IP.", warning = "This sends character progress and preferences to a third-party planner service. Only enable this for a service you trust.")
    default boolean shareProgress() { return false; }

    @ConfigItem(keyName = "serviceUrl", name = "Planner service URL",
        description = "HTTPS origin of the planner service. No path, query or credentials. No service has been deployed by default.")
    default String serviceUrl() { return ""; }

    @ConfigItem(keyName = "showFocusOverlay", name = "Show focus overlay",
        description = "Show your pinned live character goal and progress in the game view")
    default boolean showFocusOverlay() { return true; }
}
