package dev.beginnercompanion;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class CompanionLauncher {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(CompanionPlugin.class);
        RuneLite.main(args);
    }
}
