package dev.beginnercompanion;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local character/demo state. The panel calls this on a dedicated, serial I/O worker. */
final class GoalSelectionStore {
    private final Path path;
    private final Gson gson;

    private GoalSelectionStore() { this.path = null; this.gson = null; }
    static GoalSelectionStore inMemory() { return new GoalSelectionStore(); }

    GoalSelectionStore(Path path, Gson gson) { this.path = path.toAbsolutePath().normalize(); this.gson = gson.newBuilder().serializeNulls().setPrettyPrinting().create(); }

    Map<String, GoalSelection> load() throws IOException {
        if (path == null) return new LinkedHashMap<>();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.get("version").getAsJsonPrimitive().isNumber() || !"1".equals(root.get("version").getAsString())) {
                throw new IllegalArgumentException("Unsupported saved-goal file version");
            }
            Map<String, GoalSelection> accounts = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("accounts").entrySet()) {
                JsonObject account = entry.getValue().getAsJsonObject();
                Map<String, String> saved = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> goal : account.getAsJsonObject("saved_goals").entrySet()) {
                    if (!goal.getKey().matches("[a-z0-9_]{1,128}") || !goal.getValue().isJsonPrimitive()
                        || !goal.getValue().getAsJsonPrimitive().isString() || goal.getValue().getAsString().isBlank()
                        || goal.getValue().getAsString().length() > 256) throw new IllegalArgumentException("Invalid saved goal");
                    saved.put(goal.getKey(), goal.getValue().getAsString());
                }
                JsonElement focus = account.get("focus_goal_id");
                if (focus == null || !(focus.isJsonNull() || (focus.isJsonPrimitive() && focus.getAsJsonPrimitive().isString()))) {
                    throw new IllegalArgumentException("Invalid focus");
                }
                String taskId = optionalString(account, "current_task_id");
                String taskTitle = optionalString(account, "current_task_title");
                CompletionHistory history = CompletionHistory.empty();
                if (account.has("completion_history")) {
                    JsonObject data = account.getAsJsonObject("completion_history");
                    Map<String, Long> events = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> event : data.getAsJsonObject("events").entrySet()) {
                        if (!event.getValue().isJsonPrimitive() || !event.getValue().getAsJsonPrimitive().isNumber()
                            || !event.getValue().getAsString().matches("[0-9]{1,18}")) throw new IllegalArgumentException("Invalid completion time");
                        events.put(event.getKey(), event.getValue().getAsLong());
                    }
                    history = new CompletionHistory(readIds(data, "completed"), readIds(data, "incomplete"), events);
                }
                Map<String, GoalFeedback> feedback = new LinkedHashMap<>();
                if (account.has("recommendation_feedback")) {
                    for (Map.Entry<String, JsonElement> item : account.getAsJsonObject("recommendation_feedback").entrySet()) {
                        JsonObject value = item.getValue().getAsJsonObject();
                        JsonElement time = value.get("recordedAt");
                        if (!time.isJsonPrimitive() || !time.getAsJsonPrimitive().isNumber()
                            || !time.getAsString().matches("[0-9]{1,12}")) throw new IllegalArgumentException("Invalid feedback time");
                        feedback.put(item.getKey(), new GoalFeedback(optionalString(value, "kind"), time.getAsLong()));
                    }
                }
                accounts.put(entry.getKey(), new GoalSelection(saved, focus.isJsonNull() ? null : focus.getAsString(), taskId, taskTitle, history, feedback));
            }
            return accounts;
        } catch (java.nio.file.NoSuchFileException missing) {
            return new LinkedHashMap<>();
        } catch (RuntimeException exception) {
            throw new IOException("Saved goals could not be read. The existing file has been kept.", exception);
        }
    }

    void save(Map<String, GoalSelection> accounts) throws IOException {
        if (path == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject entries = new JsonObject(); root.add("accounts", entries);
        for (Map.Entry<String, GoalSelection> entry : accounts.entrySet()) {
            JsonObject account = new JsonObject();
            JsonObject saved = new JsonObject();
            entry.getValue().saved.forEach(saved::addProperty);
            account.add("saved_goals", saved);
            account.addProperty("focus_goal_id", entry.getValue().focus);
            account.addProperty("current_task_id", entry.getValue().taskId);
            account.addProperty("current_task_title", entry.getValue().taskTitle);
            account.add("completion_history", gson.toJsonTree(entry.getValue().history));
            account.add("recommendation_feedback", gson.toJsonTree(entry.getValue().feedback));
            entries.add(entry.getKey(), account);
        }
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "companion-goals-", ".tmp");
        try {
            Files.writeString(temp, gson.toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid task field");
        return value.getAsString();
    }
    private static java.util.Set<String> readIds(JsonObject object, String key) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (JsonElement value : object.getAsJsonArray(key)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid history goal");
            ids.add(value.getAsString());
        }
        return ids;
    }
}
