package dev.beginnercompanion;

import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import net.runelite.api.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiveSnapshotReaderTest {
    @Test public void readsOnlySynchronisedClientThreadObservations() {
        Fake game = new Fake();
        LiveSnapshotReader reader = new LiveSnapshotReader();
        LiveSnapshotReader.Snapshot snapshot = reader.read(game.client());
        assertEquals(24, snapshot.account.getAsJsonObject("skill_levels").size());
        assertEquals(11, snapshot.account.getAsJsonObject("quest_progress").size());
        assertEquals("not_started", snapshot.account.getAsJsonObject("quest_progress").get("tree_gnome_village").getAsString());
        assertEquals("not_started", snapshot.account.getAsJsonObject("quest_progress").get("the_grand_tree").getAsString());
        assertEquals("not_started", snapshot.account.getAsJsonObject("quest_progress").get("sheep_shearer").getAsString());
        assertEquals("not_started", snapshot.account.getAsJsonObject("quest_progress").get("dorics_quest").getAsString());
        assertEquals("not_started", snapshot.account.getAsJsonObject("quest_progress").get("cooks_assistant").getAsString());
        assertFalse(snapshot.account.get("members").getAsBoolean());
        assertEquals(0, snapshot.account.get("quest_points").getAsInt());
        assertEquals("not_started", snapshot.account.getAsJsonObject("quest_progress").get("dragon_slayer_i").getAsString());
        assertEquals(10, snapshot.account.getAsJsonObject("ardougne_easy_tasks").size());
        assertFalse(snapshot.account.getAsJsonObject("milestones").has("rune_platebody_equipped"));
        assertFalse(snapshot.account.toString().contains("Synthetic Player"));
        assertFalse(snapshot.account.toString().contains(snapshot.localKey));
        game.level = 40; game.xp = 40000; game.qp = 35; game.memberDays = 10; game.questState = 2;
        snapshot = reader.read(game.client());
        assertEquals(40, snapshot.account.getAsJsonObject("skill_levels").get("defence").getAsInt());
        assertEquals(40000, snapshot.account.getAsJsonObject("skill_xp").get("defence").getAsInt());
        assertTrue(snapshot.account.get("members").getAsBoolean());
        game.state = GameState.LOGIN_SCREEN;
        assertNull(reader.read(game.client()));
        game.state = GameState.LOGGED_IN; game.hash = -1;
        assertNull(reader.read(game.client()));
        game.hash = 42; game.level = 0;
        assertNull(reader.read(game.client()));
        game.onClientThread = false;
        assertThrows(IllegalStateException.class, () -> reader.read(game.client()));
    }

    @Test public void observedEquipmentIsRememberedPerCharacterWithoutInventingHistory() {
        Fake game = new Fake(); LiveSnapshotReader reader = new LiveSnapshotReader();
        game.wearing = true;
        assertTrue(reader.read(game.client()).account.getAsJsonObject("milestones").get("rune_platebody_equipped").getAsBoolean());
        game.wearing = false;
        assertTrue(reader.read(game.client()).account.getAsJsonObject("milestones").get("rune_platebody_equipped").getAsBoolean());
        game.hash = 43;
        assertFalse(reader.read(game.client()).account.getAsJsonObject("milestones").has("rune_platebody_equipped"));
        assertNotEquals(LiveSnapshotReader.localKey(42), LiveSnapshotReader.localKey(43));
        assertEquals(LiveSnapshotReader.localKey(42), LiveSnapshotReader.localKey(42));
    }

    @Test public void diaryBitsTrackEachTaskAndIgnoreOtherTiers() {
        String[] ids = {"essence_mine", "cake", "silk", "altar", "trawler", "training_camp",
            "rusty_sword", "wilderness_lever", "hunter_shop", "pet_check"};
        int[] bits = {0, 1, 2, 4, 5, 6, 7, 9, 11, 12};
        Fake game = new Fake(); LiveSnapshotReader reader = new LiveSnapshotReader();
        for (int i = 0; i < bits.length; i++) {
            game.diaryFlags = (1 << bits[i]) | (1 << 3) | (1 << 8) | (1 << 10);
            game.diaryCount = 1;
            JsonObject tasks = reader.read(game.client()).account.getAsJsonObject("ardougne_easy_tasks");
            for (int j = 0; j < ids.length; j++) assertEquals(ids[j], i == j, tasks.get(ids[j]).getAsBoolean());
        }
    }

    @Test public void diaryCompletionAndRewardClaimAreSeparateAndCharacterScoped() {
        Fake game = new Fake(); LiveSnapshotReader reader = new LiveSnapshotReader();
        game.diaryFlags = 0x1af7; game.diaryCount = 10; game.diaryComplete = 1;
        assertFalse(reader.read(game.client()).account.getAsJsonObject("milestones").get("ardougne_cloak_1_claimed").getAsBoolean());
        game.diaryReward = 1;
        assertTrue(reader.read(game.client()).account.getAsJsonObject("milestones").get("ardougne_cloak_1_claimed").getAsBoolean());
        game.hash = 43; game.diaryFlags = 0; game.diaryCount = 0; game.diaryComplete = 0; game.diaryReward = 0;
        assertFalse(reader.read(game.client()).account.getAsJsonObject("milestones").get("ardougne_cloak_1_claimed").getAsBoolean());
    }

    @Test public void inconsistentOrUnavailableDiaryRemainsUnknown() {
        Fake game = new Fake(); LiveSnapshotReader reader = new LiveSnapshotReader();
        game.diaryCount = 1;
        assertFalse(reader.read(game.client()).account.has("ardougne_easy_tasks"));
        game.diaryCount = 0; game.diaryReward = 1;
        assertFalse(reader.read(game.client()).account.getAsJsonObject("milestones").has("ardougne_cloak_1_claimed"));
        game.diaryReward = 0; game.diaryUnavailable = true;
        assertFalse(reader.read(game.client()).account.has("ardougne_easy_tasks"));
    }

    @Test public void chronicleObservationDoesNotInventBankOwnershipOrCharges() {
        Fake game = new Fake(); LiveSnapshotReader reader = new LiveSnapshotReader();
        assertFalse(reader.read(game.client()).account.getAsJsonObject("milestones").has("chronicle_obtained"));
        game.chronicleInInventory = true;
        assertTrue(reader.read(game.client()).account.getAsJsonObject("milestones").get("chronicle_obtained").getAsBoolean());
        game.chronicleInInventory = false;
        assertTrue(reader.read(game.client()).account.getAsJsonObject("milestones").get("chronicle_obtained").getAsBoolean());
        game.hash = 43;
        assertFalse(reader.read(game.client()).account.getAsJsonObject("milestones").has("chronicle_obtained"));
        game.chronicleEquipped = true;
        assertTrue(reader.read(game.client()).account.getAsJsonObject("milestones").get("chronicle_obtained").getAsBoolean());
        assertFalse(new LiveSnapshotReader().read(new Fake().client()).account.getAsJsonObject("milestones").has("chronicle_obtained"));
    }

    private static final class Fake {
        GameState state = GameState.LOGGED_IN;
        long hash = 42;
        int level = 1, xp = 0, qp = 0, memberDays = 0, questState = 1;
        int diaryFlags, diaryCount, diaryComplete, diaryReward;
        boolean onClientThread = true, wearing, diaryUnavailable, chronicleInInventory, chronicleEquipped;
        Client client() {
            Player player = proxy(Player.class, (method, args) -> method.equals("getName") ? "Synthetic Player" : null);
            ItemContainer equipment = proxy(ItemContainer.class, (method, args) -> method.equals("getItems") ? new Item[]{new Item(wearing ? 1127 : chronicleEquipped ? 13660 : -1, 1)} : null);
            return proxy(Client.class, (method, args) -> {
                switch (method) {
                    case "isClientThread": return onClientThread;
                    case "getGameState": return state;
                    case "getLocalPlayer": return player;
                    case "getAccountHash": return hash;
                    case "getRealSkillLevel": return level;
                    case "getSkillExperience": return xp;
                    case "getVarpValue":
                        if ((int) args[0] == 1196) return diaryFlags;
                        return (int) args[0] == VarPlayer.QUEST_POINTS ? qp : memberDays;
                    case "getVarbitValue":
                        if (diaryUnavailable) throw new IllegalStateException("Not ready");
                        switch ((int) args[0]) {
                            case 6291: return diaryCount;
                            case 4458: return diaryComplete;
                            case 4499: return diaryReward;
                            default: throw new AssertionError("Unexpected varbit");
                        }
                    case "getIntStack": return new int[]{questState};
                    case "getItemContainer":
                        if (args[0] == InventoryID.INVENTORY) return proxy(ItemContainer.class,
                            (m, a) -> m.equals("getItems") ? new Item[]{new Item(chronicleInInventory ? 13660 : -1, 1)} : null);
                        return equipment;
                    case "runScript": return null;
                    default: throw new AssertionError("Unexpected client access: " + method);
                }
            });
        }
    }

    private interface Call { Object call(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (object, method, args) -> call.call(method.getName(), args));
    }
}
