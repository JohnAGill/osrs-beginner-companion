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
        assertEquals(7, snapshot.account.getAsJsonObject("quest_progress").size());
        assertEquals("not_started", snapshot.account.getAsJsonObject("quest_progress").get("cooks_assistant").getAsString());
        assertFalse(snapshot.account.get("members").getAsBoolean());
        assertEquals(0, snapshot.account.get("quest_points").getAsInt());
        assertEquals("not_started", snapshot.account.getAsJsonObject("quest_progress").get("dragon_slayer_i").getAsString());
        assertFalse(snapshot.account.has("ardougne_easy_tasks"));
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

    private static final class Fake {
        GameState state = GameState.LOGGED_IN;
        long hash = 42;
        int level = 1, xp = 0, qp = 0, memberDays = 0, questState = 1;
        boolean onClientThread = true, wearing;
        Client client() {
            Player player = proxy(Player.class, (method, args) -> method.equals("getName") ? "Synthetic Player" : null);
            ItemContainer equipment = proxy(ItemContainer.class, (method, args) -> method.equals("getItems") ? new Item[]{new Item(wearing ? 1127 : -1, 1)} : null);
            return proxy(Client.class, (method, args) -> {
                switch (method) {
                    case "isClientThread": return onClientThread;
                    case "getGameState": return state;
                    case "getLocalPlayer": return player;
                    case "getAccountHash": return hash;
                    case "getRealSkillLevel": return level;
                    case "getSkillExperience": return xp;
                    case "getVarpValue": return (int) args[0] == VarPlayer.QUEST_POINTS ? qp : memberDays;
                    case "getIntStack": return new int[]{questState};
                    case "getItemContainer": return equipment;
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
