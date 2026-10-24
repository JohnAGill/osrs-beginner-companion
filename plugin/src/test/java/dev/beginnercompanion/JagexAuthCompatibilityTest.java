package dev.beginnercompanion;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.runelite.client.rs.auth.JagexAuthJwt;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Synthetic token only: checks our runtime libraries against RuneLite's decoder. */
public class JagexAuthCompatibilityTest {
    @Test public void decodesExpectedClaimsWithRuneLiteGson() throws Exception {
        String payload = "{\"sub\":\"synthetic-subject\",\"nickname\":\"Synthetic Account\"}";
        String token = "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(
            payload.getBytes(StandardCharsets.UTF_8)) + ".synthetic";
        JagexAuthJwt jwt = JagexAuthJwt.decode(token, RuneLiteAPI.GSON);
        assertEquals("synthetic-subject", jwt.getSub());
        assertEquals("Synthetic Account", jwt.getNickname());
    }
}
