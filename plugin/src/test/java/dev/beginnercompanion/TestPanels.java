package dev.beginnercompanion;

import com.google.gson.Gson;
import java.net.URI;
import java.nio.file.Path;
import okhttp3.OkHttpClient;

final class TestPanels {
    private static final Gson GSON = new Gson();
    static GoalSelectionStore store(Path path) { return new GoalSelectionStore(path, GSON); }
    static CompanionPanel create(URI endpoint) { return create(endpoint, GoalSelectionStore.inMemory()); }
    static CompanionPanel create(URI endpoint, GoalSelectionStore store) {
        URI origin = URI.create(endpoint.getScheme() + "://" + endpoint.getRawAuthority());
        OkHttpClient ownedClient = new OkHttpClient();
        return new CompanionPanel(new PlannerTransport(ownedClient), GSON,
            ServiceSettings.development(origin.toString()), store) {
            @Override public void close() {
                super.close();
                ownedClient.dispatcher().executorService().shutdownNow();
                ownedClient.connectionPool().evictAll();
            }
        };
    }
}
