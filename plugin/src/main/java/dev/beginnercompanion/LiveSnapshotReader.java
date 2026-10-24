package dev.beginnercompanion;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.*;

/** Called only on RuneLite's client thread after logged-in game ticks. */
final class LiveSnapshotReader {
    private final Set<String> observedWearing = new HashSet<>();

    static final class Snapshot {
        final String localKey, name;
        final JsonObject account;
        Snapshot(String localKey, String name, JsonObject account) {
            this.localKey = localKey; this.name = name; this.account = account.deepCopy();
        }
    }

    Snapshot read(Client client) {
        if (!client.isClientThread()) throw new IllegalStateException("Live data must be read on client thread");
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return null;
        long hash = client.getAccountHash();
        if (hash == -1 || hash == 0) return null;
        String key = localKey(hash);
        JsonObject account = new JsonObject(), levels = new JsonObject(), xp = new JsonObject();
        for (Skill skill : Skill.values()) {
            if (skill == Skill.OVERALL) continue;
            int level = client.getRealSkillLevel(skill);
            if (level < 1 || level > 99) return null; // Wait for initial stat synchronisation.
            String id = skill.name().toLowerCase(Locale.ROOT);
            levels.addProperty(id, level);
            int experience = client.getSkillExperience(skill);
            if (experience < 0) return null;
            xp.addProperty(id, experience);
        }
        account.add("skill_levels", levels); account.add("skill_xp", xp);
        int points = client.getVarpValue(VarPlayer.QUEST_POINTS);
        int membership = client.getVarpValue(VarPlayer.MEMBERSHIP_DAYS);
        if (points >= 0) account.addProperty("quest_points", points);
        if (membership >= 0) account.addProperty("members", membership > 0);
        JsonObject quests = new JsonObject();
        for (Quest quest : new Quest[]{Quest.DRAGON_SLAYER_I, Quest.PRINCE_ALI_RESCUE,
            Quest.PLAGUE_CITY, Quest.BIOHAZARD, Quest.RUNE_MYSTERIES, Quest.COOKS_ASSISTANT, Quest.THE_RESTLESS_GHOST}) {
            String observed = "unknown";
            try {
                QuestState state = quest.getState(client);
                observed = state == QuestState.FINISHED ? "completed" : state == QuestState.NOT_STARTED ? "not_started" : "in_progress";
            } catch (RuntimeException notYetReady) { /* Retry on subsequent game ticks. */ }
            quests.addProperty(quest.name().toLowerCase(Locale.ROOT), observed);
        }
        account.add("quest_progress", quests);
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment != null) for (Item item : equipment.getItems()) {
            if (item.getId() == 1127) observedWearing.add(key);
        }
        JsonObject milestones = new JsonObject();
        if (observedWearing.contains(key)) milestones.addProperty("rune_platebody_equipped", true);
        account.add("milestones", milestones);
        // Diary task and reward-claim mappings are not yet verified: leave them unknown.
        return new Snapshot(key, client.getLocalPlayer().getName(), account);
    }

    static String localKey(long hash) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                ("beginner-companion:" + Long.toUnsignedString(hash)).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("live:");
            for (byte value : bytes) result.append(String.format("%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
