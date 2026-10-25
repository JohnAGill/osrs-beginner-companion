package dev.beginnercompanion;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.*;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Called only on RuneLite's client thread after logged-in game ticks. */
final class LiveSnapshotReader {
    private final Set<String> observedWearing = new HashSet<>();
    private final Set<String> observedChronicle = new HashSet<>();

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
            Quest.PLAGUE_CITY, Quest.BIOHAZARD, Quest.RUNE_MYSTERIES, Quest.COOKS_ASSISTANT, Quest.THE_RESTLESS_GHOST, Quest.SHEEP_SHEARER, Quest.DORICS_QUEST, Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE}) {
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
            if (item.getId() == 13660) observedChronicle.add(key);
        }
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null) for (Item item : inventory.getItems()) {
            if (item.getId() == 13660) observedChronicle.add(key);
        }
        JsonObject milestones = new JsonObject();
        if (observedChronicle.contains(key)) milestones.addProperty("chronicle_obtained", true);
        if (observedWearing.contains(key)) milestones.addProperty("rune_platebody_equipped", true);
        account.add("milestones", milestones);
        readArdougneDiary(client, account, milestones);
        return new Snapshot(key, client.getLocalPlayer().getName(), account);
    }

    // Mapping provenance and bit positions: docs/diary-tracking.md.
    private static final String[] EASY_TASKS = {"essence_mine", "cake", "silk", "altar",
        "trawler", "training_camp", "rusty_sword", "wilderness_lever", "hunter_shop", "pet_check"};
    private static final int[] EASY_BITS = {0, 1, 2, 4, 5, 6, 7, 9, 11, 12};

    private static void readArdougneDiary(Client client, JsonObject account, JsonObject milestones) {
        try {
            int flags = client.getVarpValue(VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY);
            int count = client.getVarbitValue(VarbitID.ARDOUGNE_EASY_COUNT);
            int complete = client.getVarbitValue(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE);
            int reward = client.getVarbitValue(VarbitID.ARDOUGNE_EASY_REWARD);
            JsonObject tasks = new JsonObject();
            int observed = 0;
            for (int i = 0; i < EASY_TASKS.length; i++) {
                boolean done = (flags & (1 << EASY_BITS[i])) != 0;
                tasks.addProperty(EASY_TASKS[i], done);
                if (done) observed++;
            }
            // Reject inconsistent/transitional observations rather than invent progress.
            if (count != observed || complete != (observed == 10 ? 1 : 0)
                || reward < 0 || reward > 1 || (reward == 1 && complete != 1)) return;
            account.add("ardougne_easy_tasks", tasks);
            milestones.addProperty("ardougne_cloak_1_claimed", reward == 1);
        } catch (RuntimeException notYetReady) { /* Leave unknown and retry next snapshot. */ }
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
