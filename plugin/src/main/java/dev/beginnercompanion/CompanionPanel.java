package dev.beginnercompanion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.beginnercompanion.bridge.PlannerClient;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/** Swing owns view state; Python owns goal descriptions and progress evaluation. */
public class CompanionPanel extends PluginPanel {
    private static final Color BACKGROUND = new Color(30, 30, 30);
    private static final Color GOLD = new Color(239, 184, 70);
    private static final String[] DEMO_IDS = {"fresh", "partial", "complete", "unclaimed", "unknown"};
    private final JComboBox<String> demo = new JComboBox<>(new String[]{
        "Fresh F2P account", "Partial members account", "All goals completed", "Unlocks ready, not claimed", "Unknown progress", "Live character"});
    private final JButton check = new JButton("Check demo account");
    private final JButton characterData = new JButton("Character data");
    private final JCheckBox combat = interest("Combat");
    private final JCheckBox quests = interest("Quests and stories");
    private final JCheckBox exploration = interest("Exploration");
    private final JCheckBox utility = interest("Useful unlocks");
    private final JComboBox<String> commitment = new JComboBox<>(new String[]{
        "Any commitment", "A short activity", "A few sessions", "A longer ambition"});
    private boolean editingPreferences;
    private final JLabel connection = new JLabel("Not checked");
    private final JPanel rows = column();
    private final JPanel selectionHeader = column();
    private final JPanel feedbackManager = column();
    private final JTabbedPane tabs = new JTabbedPane() {
        @Override public java.awt.Dimension getPreferredSize() {
            Component selected = getSelectedComponent();
            // Hidden catalogue/detail pages must not leave a tall empty Focus page.
            return new java.awt.Dimension(210, (selected == null ? 0 : selected.getPreferredSize().height) + 36);
        }
    };
    private final JPanel exploreTab = column(), savedTab = column();
    private final JTextField goalSearch = new JTextField();
    private final JComboBox<String> goalAccess = new JComboBox<>(new String[]{"All goals", "Free-to-play", "Members"});
    private final GoalSelectionStore selectionStore;
    private final java.util.Map<String, GoalSelection> selections = new java.util.LinkedHashMap<>();
    private final ExecutorService storageExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "companion-saved-goals"); thread.setDaemon(true); return thread;
    });
    private boolean selectionsLoaded, loadingSelections, saving, savedOnly;
    private String selectionError;
    private String resultMessage = "Click Check demo account to load the goal browser from Python.";
    private final PlannerClient.Transport transport;
    private final Gson gson;
    private ServiceSettings service;
    private final JTextArea serviceNotice = text("", false);
    private final JTextArea serviceStatus = text("", false);
    private String session = UUID.randomUUID().toString();
    private LiveSnapshotReader.Snapshot liveAccount;
    private boolean liveRefreshQueued;
    private long retryAfter;
    private long nextRefreshAt;
    private long feedbackCheckedAt;
    private final JTextArea sourceLabel = text("Demo mode: fictional accounts only.", false);
    private int revision;
    private boolean closed;
    private String selectedGoal;
    private PlannerClient.GoalsResult snapshot;
    private boolean progressFresh;
    private final java.util.Set<String> expandedGroups = new java.util.HashSet<>();
    private CompletableFuture<PlannerClient.GoalsResult> pending;
    private java.util.function.Consumer<FocusOverlayState> focusListener = ignored -> {};

    void setFocusListener(java.util.function.Consumer<FocusOverlayState> listener) {
        focusListener = listener;
        publishFocus();
    }

    private void publishFocus() {
        GoalSelection chosen = currentSelection();
        if (closed || !isLive() || liveAccount == null || !selectionsLoaded || chosen.focus == null) {
            focusListener.accept(FocusOverlayState.HIDDEN);
            return;
        }
        String unavailable = "Checking Python...".equals(connection.getText()) ? "Updating progress..."
            : "Service unavailable".equals(connection.getText()) ? "Python unavailable" : "Progress unavailable";
        focusListener.accept(FocusOverlayState.from(liveAccount.localKey, chosen.saved.get(chosen.focus),
            progressFresh ? findGoal(chosen.focus) : null, unavailable, chosen));
    }

    CompanionPanel(PlannerClient.Transport transport, Gson gson, ServiceSettings service, GoalSelectionStore selectionStore) {
        this.transport = transport;
        this.gson = gson;
        this.service = service;
        this.selectionStore = selectionStore;
        setLayout(new BorderLayout());
        JPanel content = column();
        content.setBorder(new EmptyBorder(10, 8, 10, 8));
        add(content, BorderLayout.NORTH);
        content.add(text("Beginner Companion", true));
        content.add(sourceLabel);
        updateServiceNotice();
        content.add(serviceStatus);
        JPanel setup = column();
        JPanel privacy = column(); privacy.add(serviceNotice);
        setup.add(disclosure("planner-privacy", "Planner connection & privacy", privacy, false));
        demo.setAlignmentX(Component.LEFT_ALIGNMENT);
        demo.setMaximumSize(new java.awt.Dimension(200, 28));
        check.setAlignmentX(Component.LEFT_ALIGNMENT);
        setup.add(demo);
        setup.add(Box.createVerticalStrut(8));
        JToggleButton tailor = new JToggleButton("Your preferences (optional)");
        tailor.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel questions = column();
        questions.add(text("What sounds enjoyable? Choose any, or leave blank for a mix.", false));
        for (JCheckBox interest : new JCheckBox[]{combat, quests, exploration, utility}) {
            questions.add(interest);
            interest.addActionListener(event -> preferencesChanged());
        }
        questions.add(text("How much remaining work appeals?", false));
        commitment.setAlignmentX(Component.LEFT_ALIGNMENT);
        commitment.setMaximumSize(new java.awt.Dimension(200, 28));
        questions.add(commitment);
        questions.add(text("Matches the work left on your character, without predicting hours or a number of sessions.", false));
        commitment.addActionListener(event -> preferencesChanged());
        questions.add(button("Show a mixture", () -> setPreferences(new String[0], 0)));
        questions.add(text("Answers last for this session. Live suggestions update automatically.", false));
        questions.setVisible(false);
        tailor.addActionListener(event -> {
            questions.setVisible(tailor.isSelected());
            content.revalidate(); content.repaint();
        });
        setup.add(tailor);
        setup.add(questions);
        setup.add(Box.createVerticalStrut(8));
        setup.add(check);
        setup.add(Box.createVerticalStrut(8));
        connection.setAlignmentX(Component.LEFT_ALIGNMENT);
        connection.setForeground(Color.LIGHT_GRAY);
        setup.add(connection);
        characterData.setAlignmentX(Component.LEFT_ALIGNMENT);
        characterData.addActionListener(event -> {
            if (!isLive() || liveAccount == null) return;
            StringBuilder report = new StringBuilder();
            report.append("Quest points: ").append(liveAccount.account.get("quest_points")).append("\n");
            report.append("Membership: ").append(liveAccount.account.get("members").getAsBoolean() ? "Member" : "Free-to-play").append("\n\nSkills (level / XP)\n");
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> skill : liveAccount.account.getAsJsonObject("skill_levels").entrySet()) {
                report.append(skill.getKey()).append(": ").append(skill.getValue()).append(" / ")
                    .append(liveAccount.account.getAsJsonObject("skill_xp").get(skill.getKey())).append("\n");
            }
            report.append("\nQuests relevant to these goals\n");
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> quest : liveAccount.account.getAsJsonObject("quest_progress").entrySet()) {
                report.append(quest.getKey().replace('_', ' ')).append(": ").append(quest.getValue().getAsString().replace('_', ' ')).append("\n");
            }
            report.append("\nEasy Ardougne diary tasks and cloak reward claims are read from game variables. Inconsistent or unavailable readings stay unknown.\nPast equipment use and Chronicle ownership are unknown unless observed during this plugin session. Chronicle charges and bank contents are not read.");
            JTextArea data = new JTextArea(report.toString(), 24, 38);
            data.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(data), "Live character observations", JOptionPane.INFORMATION_MESSAGE);
        });
        setup.add(characterData);
        setup.add(disclosure("feedback-settings", "Recommendation feedback", feedbackManager, false));
        content.add(disclosure("setup", "Settings", setup, false));
        tabs.addTab("Focus", selectionHeader);
        tabs.addTab("Explore", exploreTab);
        tabs.addTab("Saved", savedTab);
        tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabs.setMaximumSize(new java.awt.Dimension(210, Integer.MAX_VALUE));
        goalSearch.setToolTipText("Search goal titles and summaries");
        goalSearch.setAlignmentX(Component.LEFT_ALIGNMENT);
        goalSearch.setMaximumSize(new java.awt.Dimension(200, 28));
        goalAccess.setAlignmentX(Component.LEFT_ALIGNMENT);
        goalAccess.setMaximumSize(new java.awt.Dimension(200, 28));
        exploreTab.add(text("Find a goal", true));
        exploreTab.add(goalSearch);
        exploreTab.add(goalAccess);
        goalSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { filterGoals(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { filterGoals(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { filterGoals(); }
        });
        goalAccess.addActionListener(event -> filterGoals());
        exploreTab.add(rows);
        tabs.addChangeListener(event -> {
            savedOnly = tabs.getSelectedIndex() == 2;
            selectedGoal = null;
            if (tabs.getSelectedIndex() != 0) {
                (savedOnly ? savedTab : exploreTab).add(rows);
            }
            resetScroll(); render();
        });
        content.add(tabs);
        check.addActionListener(event -> evaluate());
        demo.addActionListener(event -> {
            session = UUID.randomUUID().toString(); invalidateRequest();
            if (isLive()) evaluate();
        });
        render();
        loadSelections();
    }

    private boolean isLive() { return demo.getSelectedIndex() == 5; }
    private String demoId() { return isLive() ? (liveAccount == null ? "live:logged-out" : liveAccount.localKey) : DEMO_IDS[demo.getSelectedIndex()]; }
    void enableLiveMode() { demo.setSelectedIndex(5); }

    void setLiveSnapshot(LiveSnapshotReader.Snapshot next) {
        if (closed) return;
        boolean switched = liveAccount == null ? next != null : next == null || !liveAccount.localKey.equals(next.localKey);
        boolean changed = switched || (next != null && !next.account.equals(liveAccount.account));
        liveAccount = next;
        if (!isLive()) return;
        if (switched) { session = UUID.randomUUID().toString(); invalidateRequest(); }
        if (next == null) { if (!switched) render(); return; }
        sourceLabel.setText("Live character: " + (next.name == null ? "logged in" : next.name));
        if (changed) {
            if (pending != null) { liveRefreshQueued = true; progressFresh = false; publishFocus(); }
            else if (System.currentTimeMillis() >= retryAfter) evaluate();
            else liveRefreshQueued = true;
        } else if (pending == null && System.currentTimeMillis() >= retryAfter && (snapshot == null || liveRefreshQueued
            || currentSelection().feedback.values().stream().anyMatch(value -> value.expiresAt() > feedbackCheckedAt
                && value.expiresAt() <= System.currentTimeMillis() / 1000))) evaluate();
    }
    private GoalSelection currentSelection() { return selections.getOrDefault(demoId(), GoalSelection.empty()); }

    private void loadSelections() {
        if (closed || selectionsLoaded || loadingSelections) return;
        loadingSelections = true; selectionError = null; render();
        CompletableFuture.supplyAsync(() -> {
            try { return selectionStore.load(); }
            catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, storageExecutor).whenComplete((loaded, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed) return;
            loadingSelections = false;
            if (failure == null) { selections.clear(); selections.putAll(loaded); selectionsLoaded = true; }
            else selectionError = "Could not load saved goals. Existing data has been kept. Fix the local file, then retry loading.";
            render();
            if (failure == null) recordHistory();
        }));
    }

    private void changeSelection(GoalSelection next) {
        if (closed || !selectionsLoaded || saving || (isLive() && liveAccount == null)) return;
        java.util.Map<String, GoalSelection> updated = new java.util.LinkedHashMap<>(selections);
        String accountKey = demoId();
        boolean newCompletions = !currentSelection().history.events.equals(next.history.events);
        boolean feedbackChanged = !currentSelection().feedback.equals(next.feedback);
        updated.put(accountKey, next);
        saving = true; selectionError = null; render();
        CompletableFuture.runAsync(() -> {
            try { selectionStore.save(updated); }
            catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, storageExecutor).whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed) return;
            saving = false;
            if (failure == null) { selections.clear(); selections.putAll(updated); }
            else selectionError = "Could not save changes. Previous choices kept. Check the local file is writable and try again.";
            render();
            if (failure == null) {
                recordHistory();
                if (feedbackChanged && accountKey.equals(demoId())) {
                    revision++;
                    if (pending != null) pending.cancel(true);
                    pending = null; liveRefreshQueued = false;
                    evaluate();
                } else if (newCompletions && accountKey.equals(demoId())) {
                    if (pending != null && isLive()) liveRefreshQueued = true;
                    else evaluate();
                }
            }
        }));
    }

    private void recordHistory() {
        if (closed || !selectionsLoaded || saving || !progressFresh || snapshot == null
            || (isLive() && liveAccount == null)) return;
        GoalSelection chosen = currentSelection();
        CompletionHistory next = chosen.history.observe(snapshot, System.currentTimeMillis() / 1000);
        if (next != chosen.history) changeSelection(chosen.withHistory(next));
    }

    private JButton selectionButton(String label, Runnable action) {
        JButton result = button(label, action);
        result.setEnabled(selectionsLoaded && !saving && !closed && (!isLive() || liveAccount != null));
        return result;
    }

    private PlannerClient.GoalResult findGoal(String id) {
        if (snapshot != null) for (PlannerClient.GoalResult row : snapshot.goals) {
            if (row.goal.id.equals(id)) return row;
        }
        return null;
    }

    private void renderSelectionHeader() {
        selectionHeader.removeAll();
        if (snapshot != null && !progressFresh && "Service unavailable".equals(connection.getText()))
            selectionHeader.add(text("Last confirmed results are out of date. Refresh when the planner is available.", false));
        selectionHeader.add(Box.createVerticalStrut(10));
        if (!selectionsLoaded) {
            selectionHeader.add(text(selectionError == null ? "Loading saved goals..." : selectionError, false));
            if (selectionError != null) selectionHeader.add(button("Retry loading saved goals", this::loadSelections));
        } else {
            GoalSelection chosen = currentSelection();
            selectionHeader.add(text("Current focus", true));
            if (chosen.focus == null) {
                selectionHeader.add(text("No goal pinned. Explore goals and pin one to choose what to work on here.", false));
                selectionHeader.add(button("Explore goals", this::showBrowser));
            }
            else {
                selectionHeader.add(text(chosen.saved.get(chosen.focus), true));
                PlannerClient.GoalResult focus = findGoal(chosen.focus);
                selectionHeader.add(text(focus == null ? "Check demo account to refresh progress." : state(focus.status) + "\n" + progress(focus), false));
                if (focus != null) addRemainingInvestment(selectionHeader, focus, false);
                if (chosen.taskId != null) {
                    selectionHeader.add(text("Current task: " + chosen.taskTitle, true));
                    selectionHeader.add(text(CurrentTask.status(chosen, progressFresh ? focus : null), false));
                    selectionHeader.add(selectionButton("Clear current task", () -> changeSelection(currentSelection().clearTask())));
                }
                if (focus != null) {
                    JPanel suggestions = column();
                    addNextSteps(suggestions, focus);
                    if (chosen.taskId == null) selectionHeader.add(suggestions);
                    else selectionHeader.add(disclosure("other-steps", "Choose another task", suggestions, false));
                }
                JButton open = button("Open focus", () -> selectGoal(chosen.focus));
                open.setEnabled(focus != null); selectionHeader.add(open);
                selectionHeader.add(selectionButton("Unpin focus", () -> changeSelection(currentSelection().unpin())));
                if (focus != null) addFeedback(selectionHeader, focus);
            }
            if (saving) selectionHeader.add(text("Saving your choices...", false));
            if (selectionError != null) selectionHeader.add(text(selectionError, false));
            JPanel history = column();
            if (chosen.history.events.isEmpty()) history.add(text("No new completions observed yet. Goals already completed on first observation form a baseline.", false));
            chosen.history.events.entrySet().stream().sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10).forEach(event -> {
                    PlannerClient.GoalResult goal = findGoal(event.getKey());
                    history.add(text(goal == null ? event.getKey().replace('_', ' ') : goal.goal.title, true));
                    history.add(text("Observed " + java.time.Instant.ofEpochSecond(event.getValue()).atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate().toString(), false));
                });
            selectionHeader.add(disclosure("history", "Completion history", history, false));
        }
        selectionHeader.revalidate(); selectionHeader.repaint();
    }

    private void addSelectionActions(String id, String title) {
        GoalSelection chosen = currentSelection();
        if (id.equals(chosen.focus)) rows.add(text("Your current focus", true));
        else if (chosen.saved.containsKey(id)) rows.add(text("Saved", true));
        rows.add(selectionButton(chosen.saved.containsKey(id) ? "Remove saved goal" : "Save goal", () -> {
            GoalSelection now = currentSelection();
            changeSelection(now.saved.containsKey(id) ? now.remove(id) : now.save(id, title));
        }));
        rows.add(selectionButton(id.equals(chosen.focus) ? "Unpin focus" : "Pin as focus", () -> {
            GoalSelection now = currentSelection();
            changeSelection(id.equals(now.focus) ? now.unpin() : now.pin(id, title));
        }));
    }

    private void renderSaved() {
        section("Saved goals");
        if (!selectionsLoaded) { rows.add(text("Your saved goals will appear once local data is loaded.", false)); return; }
        if (currentSelection().saved.isEmpty()) {
            rows.add(text("No saved goals yet. Explore a goal and choose Save goal or Pin as focus.", false));
        }
        for (java.util.Map.Entry<String, String> entry : currentSelection().saved.entrySet()) {
            PlannerClient.GoalResult goal = findGoal(entry.getKey());
            section(goal == null ? entry.getValue() : goal.goal.title);
            rows.add(text(goal == null ? (snapshot == null ? "Check demo account to refresh progress." : "This saved goal is not in the current catalogue.") : state(goal.status) + "\n" + progress(goal), false));
            if (goal != null) addRemainingInvestment(rows, goal, false);
            JButton view = button("View goal", () -> selectGoal(entry.getKey()));
            view.setEnabled(goal != null); rows.add(view);
            addSelectionActions(entry.getKey(), goal == null ? entry.getValue() : goal.goal.title);
        }
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setBackground(BACKGROUND);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JCheckBox interest(String label) {
        JCheckBox box = new JCheckBox(label);
        box.setBackground(BACKGROUND); box.setForeground(Color.LIGHT_GRAY);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    private void preferencesChanged() {
        if (!editingPreferences) { invalidateRequest(); if (isLive()) evaluate(); }
    }

    private static JTextArea text(String value, boolean heading) {
        // These are display labels. A newly laid-out caret must not scroll
        // the containing sidebar to make itself visible after a refresh.
        JTextArea area = new JTextArea(value) {
            @Override public void scrollRectToVisible(java.awt.Rectangle rectangle) { }
        };
        area.setEditable(false); area.setFocusable(false); area.setOpaque(false);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setForeground(heading ? GOLD : Color.LIGHT_GRAY);
        area.setSize(200, Short.MAX_VALUE);
        int height = area.getPreferredSize().height;
        area.setPreferredSize(new java.awt.Dimension(200, height));
        area.setMinimumSize(new java.awt.Dimension(200, height));
        area.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, height));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private static JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new java.awt.Dimension(200, 28));
        button.setPreferredSize(new java.awt.Dimension(200, 28));
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel disclosure(String key, String title, JPanel body, boolean initiallyOpen) {
        JPanel group = column();
        group.add(Box.createVerticalStrut(6));
        boolean open = expandedGroups.contains(key) || initiallyOpen;
        JToggleButton toggle = new JToggleButton((open ? "▾ " : "▸ ") + title, open);
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.setMaximumSize(new java.awt.Dimension(200, 28));
        toggle.setToolTipText(title);
        body.setBorder(new EmptyBorder(6, 4, 6, 0));
        body.setVisible(open);
        toggle.addActionListener(event -> {
            if (toggle.isSelected()) expandedGroups.add(key); else expandedGroups.remove(key);
            toggle.setText((toggle.isSelected() ? "▾ " : "▸ ") + title);
            body.setVisible(toggle.isSelected());
            group.revalidate(); group.repaint();
            render();
        });
        group.add(toggle); group.add(body);
        return group;
    }

    private void section(String title) {
        rows.add(Box.createVerticalStrut(12));
        rows.add(text(title, true));
    }

    private static String progress(PlannerClient.GoalResult result) {
        PlannerClient.Progress p = result.progress;
        return p.satisfied + " / " + p.total + " milestones met; " + p.unknown + " unknown";
    }

    private static String state(String status) {
        switch (status) {
            case "completed": return "Completed";
            case "incomplete": return "Not completed";
            default: return "Completion unknown";
        }
    }

    private void render() {
        JScrollPane scroll = getScrollPane();
        java.awt.Point position = scroll == null ? null : scroll.getViewport().getViewPosition();
        renderContents();
        if (scroll != null) {
            // Complete the new layout before Swing can clamp the viewport to an
            // intermediate empty/short size. Restore synchronously, never in a
            // deferred callback that could overwrite a subsequent user scroll.
            java.awt.Component view = scroll.getViewport().getView();
            invalidateTree((java.awt.Container) view);
            java.awt.Dimension extent = scroll.getViewport().getExtentSize();
            int height = Math.max(extent.height, view.getPreferredSize().height);
            java.awt.Dimension size = new java.awt.Dimension(Math.max(extent.width, getPreferredSize().width), height);
            scroll.getViewport().setViewSize(size);
            layoutTree((java.awt.Container) view);
            scroll.getViewport().setViewPosition(new java.awt.Point(0,
                Math.min(position.y, Math.max(0, height - extent.height))));
        }
    }

    private static void layoutTree(java.awt.Container parent) {
        parent.doLayout();
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof java.awt.Container) layoutTree((java.awt.Container) child);
        }
    }

    private static void invalidateTree(java.awt.Container parent) {
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof java.awt.Container) invalidateTree((java.awt.Container) child);
        }
        parent.invalidate();
    }

    private void renderContents() {
        publishFocus();
        characterData.setEnabled(isLive() && liveAccount != null && !closed);
        renderSelectionHeader();
        renderFeedbackManager();
        rows.removeAll();
        if (snapshot != null && !progressFresh && "Service unavailable".equals(connection.getText()))
            rows.add(text("Last confirmed results are out of date. Refresh when the planner is available.", false));
        if (tabs.getSelectedIndex() == 0) {
            if (snapshot == null) selectionHeader.add(text(resultMessage, false));
            return;
        }
        if (snapshot == null) {
            if (savedOnly) renderSaved();
            else rows.add(text(resultMessage, false));
            rows.revalidate(); rows.repaint(); return;
        }
        PlannerClient.GoalResult detail = null;
        for (PlannerClient.GoalResult result : snapshot.goals) {
            if (result.goal.id.equals(selectedGoal)) detail = result;
        }
        if (detail == null) {
            selectedGoal = null;
            if (savedOnly) renderSaved();
            else {
                section("Explore goals");
                rows.add(text(snapshot.recommendation_summary, false));
                if (!currentSelection().feedback.isEmpty()) rows.add(text("Saved feedback may hide or pause goals. Reset it in Settings → Recommendation feedback.", false));
                int matches = 0;
                for (PlannerClient.GoalResult result : snapshot.goals) {
                    if ("not_interested".equals(result.recommendation.feedback_status)) continue;
                    String query = goalSearch.getText().strip().toLowerCase(java.util.Locale.ROOT);
                    if (!(result.goal.title + " " + result.goal.summary).toLowerCase(java.util.Locale.ROOT).contains(query)
                        || (goalAccess.getSelectedIndex() == 1 && result.goal.members_only)
                        || (goalAccess.getSelectedIndex() == 2 && !result.goal.members_only)) continue;
                    matches++;
                    section(result.goal.title);
                    if (result.recommendation.recommended) rows.add(text("Suggested for you", true));
                    rows.add(text(result.goal.members_only ? "Members" : "Free-to-play", false));
                    rows.add(text(result.goal.summary, false));
                    rows.add(text(state(result.status) + "\n" + progress(result), false));
                    addRemainingInvestment(rows, result, false);
                    if (!result.recommendation.feedback_reason.isEmpty()) rows.add(text(result.recommendation.feedback_reason, false));
                    rows.add(button("View goal", () -> selectGoal(result.goal.id)));
                    addFeedback(rows, result);

                }
                if (matches == 0) rows.add(text("No matching goals. Try another search or access filter.", false));
            }
        } else {
            rows.add(Box.createVerticalStrut(10));
            rows.add(button(savedOnly ? "Back to saved goals" : "Back to goals", () -> {
                if (savedOnly) showSaved(); else showBrowser();
            }));
            section(detail.goal.title);
            JPanel match = column();
            for (String reason : detail.recommendation.reasons) match.add(text(reason, false));
            for (String note : detail.recommendation.considerations) match.add(text(note, false));
            rows.add(disclosure(detail.goal.id + ":match", "Why this goal fits", match, false));
            rows.add(text(detail.goal.members_only ? "Members" : "Free-to-play", false));
            rows.add(text(state(detail.status) + "\n" + progress(detail), false));
            addRemainingInvestment(rows, detail, true);
            addSelectionActions(detail.goal.id, detail.goal.title);
            addFeedback(rows, detail);
            rows.add(text("Milestone counts are checks, not an estimate of effort.", false));
            addNextSteps(rows, detail);
            JPanel overview = column();
            for (String benefit : detail.goal.benefits) overview.add(text("• " + benefit, false));
            overview.add(Box.createVerticalStrut(8));
            overview.add(text("Overall investment from a new account", true));
            overview.add(text(detail.goal.investment, false));
            rows.add(disclosure(detail.goal.id + ":overview", "Benefits and investment", overview, false));
            section("Requirements and progress");
            for (PlannerClient.GoalRequirement requirement : detail.requirements) addRequirement(rows, requirement);
            JPanel preparation = column();
            for (String note : detail.goal.preparation) {
                preparation.add(text("• " + note, false)); preparation.add(Box.createVerticalStrut(6));
            }
            rows.add(disclosure(detail.goal.id + ":preparation", "Preparation and beginner notes", preparation, false));
            JPanel sources = column();
            for (String source : detail.goal.sources) sources.add(text(source, false));
            sources.add(text("Reviewed " + detail.goal.reviewed, false));
            rows.add(disclosure(detail.goal.id + ":sources", "Sources", sources, false));
        }
        JPanel about = column();
        for (String note : snapshot.notes) about.add(text(note, false));
        rows.add(disclosure("about", "About progress tracking", about, false));
        rows.revalidate(); rows.repaint();
    }

    private void addRemainingInvestment(JPanel target, PlannerClient.GoalResult result, boolean includeDetails) {
        PlannerClient.RemainingInvestment remaining = result.remaining_investment;
        if (remaining == null) return;
        target.add(text("Work remaining: " + remaining.summary, false));
        if (includeDetails) {
            JPanel details = column();
            for (String line : remaining.details) {
                details.add(text(line, false)); details.add(Box.createVerticalStrut(6));
            }
            details.add(text(remaining.caveat, false));
            details.add(Box.createVerticalStrut(6));
            details.add(text("Commitment fit: " + remaining.commitment_band.replace('_', ' '), true));
            details.add(text(remaining.commitment_reason, false));
            target.add(disclosure(result.goal.id + ":remaining", "Work remaining details", details, false));
        }
    }

    private void addFeedback(JPanel target, PlannerClient.GoalResult goal) {
        JPanel options = column();
        options.add(text("Not interested hides this goal until reset. Maybe later pauses recommendations for 24 hours. Too much work pauses them for seven days and favours confirmed short alternatives.", false));
        if (!goal.recommendation.feedback_reason.isEmpty()) options.add(text(goal.recommendation.feedback_reason, true));
        String[] labels = {"Not interested", "Maybe later", "Too much work right now"};
        String[] kinds = {"not_interested", "maybe_later", "too_much_work"};
        for (int i = 0; i < labels.length; i++) {
            String kind = kinds[i];
            options.add(selectionButton(labels[i], () -> changeSelection(currentSelection().feedback(goal.goal.id, kind, System.currentTimeMillis() / 1000))));
        }
        if (currentSelection().feedback.containsKey(goal.goal.id)) {
            options.add(selectionButton("Reset feedback for this goal", () -> changeSelection(currentSelection().resetFeedback(goal.goal.id))));
        }
        target.add(disclosure(goal.goal.id + ":feedback", "Recommendation feedback", options, false));
    }

    private void renderFeedbackManager() {
        feedbackManager.removeAll();
        GoalSelection chosen = currentSelection();
        feedbackManager.add(text("Feedback is saved for this character. Resetting it keeps preference answers, goals, tasks and history.", false));
        if (chosen.feedback.isEmpty()) feedbackManager.add(text("No recommendation feedback saved.", false));
        chosen.feedback.forEach((id, value) -> {
            PlannerClient.GoalResult goal = findGoal(id);
            feedbackManager.add(text(goal == null ? id.replace('_', ' ') : goal.goal.title, true));
            long expires = value.expiresAt();
            String status = "not_interested".equals(value.kind) ? "Not interested — until reset"
                : value.kind.replace('_', ' ') + (expires <= System.currentTimeMillis() / 1000 ? " — expired; updates on refresh" : " — until " + java.time.Instant.ofEpochSecond(expires).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0));
            feedbackManager.add(text(status, false));
            feedbackManager.add(selectionButton("Reset this feedback", () -> changeSelection(currentSelection().resetFeedback(id))));
        });
        JButton reset = selectionButton("Reset all feedback", () -> changeSelection(currentSelection().resetFeedback(null)));
        reset.setEnabled(reset.isEnabled() && !chosen.feedback.isEmpty());
        feedbackManager.add(reset);
        feedbackManager.revalidate(); feedbackManager.repaint();
    }

    private void addNextSteps(JPanel target, PlannerClient.GoalResult result) {
        target.add(text("Suggested next steps", true));
        if (!progressFresh) {
            target.add(text("Updating suggestions from your latest progress...", false));
        } else if ("completed".equals(result.status)) {
            target.add(text("Goal complete! Choose another focus whenever you like.", false));
        } else if (result.next_steps == null || result.next_steps.length == 0) {
            target.add(text("Review your requirements and progress below.", false));
        } else {
            target.add(text("Choose what appeals to you. These are options, in no required order.", false));
            for (PlannerClient.NextStep step : result.next_steps) {
                target.add(text("• " + step.title, true));
                JPanel explanation = column();
                explanation.add(text(step.reason, false));
                target.add(disclosure(result.goal.id + ":step:" + step.id, "Why this step?", explanation, false));
                if (result.goal.id.equals(currentSelection().focus)) {
                    boolean selected = step.id.equals(currentSelection().taskId);
                    JButton choose = selectionButton(selected ? "Current task" : "Work on this", () -> {
                        // A refresh or goal switch may have invalidated this button's data.
                        if (progressFresh && result == findGoal(currentSelection().focus)) {
                            changeSelection(currentSelection().task(step.id, step.title));
                        }
                    });
                    choose.setEnabled(choose.isEnabled() && !selected);
                    target.add(choose);
                }
                target.add(Box.createVerticalStrut(6));
            }
        }
    }

    private void addRequirement(JPanel target, PlannerClient.GoalRequirement requirement) {
        target.add(Box.createVerticalStrut(8));
        String marker = requirement.status.equals("satisfied") ? "Met" : requirement.status.equals("missing") ? "Still needed" : "Unknown";
        target.add(text(marker + " — " + requirement.label, true));
        if (!"milestone".equals(requirement.category)) {
            target.add(text(requirement.category.equals("eligibility") ? "Access requirement (outside milestone count)" : "Quest entry context (outside milestone count)", false));
        }
        target.add(text("Observed: " + display(requirement.observed) + "\nRequired: " + display(requirement.required), false));
        if (!requirement.note.isEmpty()) target.add(text(requirement.note, false));
        if (requirement.children.length > 0) {
            String expansionKey = selectedGoal + ":" + requirement.id;
            boolean expanded = expandedGroups.contains(expansionKey);
            JToggleButton expand = new JToggleButton(expanded ? "Hide diary tasks" : "Show diary tasks");
            expand.setSelected(expanded);
            expand.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel children = column();
            for (PlannerClient.GoalRequirement child : requirement.children) addRequirement(children, child);
            children.setVisible(expanded);
            expand.addActionListener(event -> {
                if (expand.isSelected()) expandedGroups.add(expansionKey);
                else expandedGroups.remove(expansionKey);
                children.setVisible(expand.isSelected());
                expand.setText(expand.isSelected() ? "Hide diary tasks" : "Show diary tasks");
                target.revalidate(); target.repaint();
            });
            target.add(expand); target.add(children);
        }
    }

    private static String display(com.google.gson.JsonElement value) {
        if (value == null || value.isJsonNull()) return "unknown";
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean() ? "yes" : "no";
        return value.getAsString().replace('_', ' ');
    }

    private void invalidateRequest() {
        revision++;
        if (pending != null) pending.cancel(true);
        pending = null; snapshot = null; progressFresh = false; selectedGoal = null; liveRefreshQueued = false; retryAfter = 0;
        expandedGroups.clear();
        resetScroll();
        check.setText(isLive() ? "Refresh live data" : "Check demo account");
        check.setEnabled(!closed && (!isLive() || liveAccount != null));
        sourceLabel.setText(isLive() ? (liveAccount == null ? "Live mode: log in to read character data." : "Live character: " + liveAccount.name) : "Demo mode: fictional accounts only.");
        connection.setText("Not checked");
        resultMessage = isLive() ? (liveAccount == null ? "Log in to load your character. Live progress clears on logout." : "Live data is ready to refresh.") : "Click Check demo account to refresh suggestions for these answers.";
        render();
    }

    void evaluate() {
        if (closed || pending != null || (isLive() && liveAccount == null)) return;
        if (!service.canSend()) {
            progressFresh = false;
            connection.setText("Planner disabled");
            resultMessage = service.disclosure();
            render(); return;
        }
        if (isLive() && System.currentTimeMillis() < nextRefreshAt) { liveRefreshQueued = true; return; }
        liveRefreshQueued = false;
        nextRefreshAt = System.currentTimeMillis() + ("https".equals(service.endpoint.getScheme()) ? 5000 : 0);
        int requestedRevision = ++revision;
        long requestedAt = System.currentTimeMillis() / 1000;
        try {
            String name = demoId();
            JsonObject request;
            if (isLive()) {
                request = new JsonObject();
                request.addProperty("protocol_version", 1);
                request.addProperty("goal_id", "all");
                request.addProperty("source", "live");
                request.add("account", liveAccount.account.deepCopy());
            } else try (InputStream stream = getClass().getResourceAsStream("/fixtures/goals-" + name + ".request.json")) {
                if (stream == null) throw new IllegalStateException("Demo fixture is missing.");
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    request = JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
            request.addProperty("session_id", session); request.addProperty("revision", requestedRevision);
            JsonObject preferences = new JsonObject();
            com.google.gson.JsonArray interests = new com.google.gson.JsonArray();
            if (combat.isSelected()) interests.add("combat");
            if (quests.isSelected()) interests.add("quests");
            if (exploration.isSelected()) interests.add("exploration");
            if (utility.isSelected()) interests.add("utility");
            preferences.add("interests", interests);
            preferences.addProperty("commitment", new String[]{"any", "short", "few_sessions", "long"}[commitment.getSelectedIndex()]);
            request.add("preferences", preferences);
            com.google.gson.JsonArray history = new com.google.gson.JsonArray();
            currentSelection().history.events.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed()).limit(50).forEach(event -> {
                    JsonObject completion = new JsonObject();
                    completion.addProperty("goal_id", event.getKey());
                    completion.addProperty("completed_at", event.getValue());
                    history.add(completion);
                });
            request.add("completion_history", history);
            JsonObject feedback = new JsonObject();
            currentSelection().feedback.forEach((id, value) -> {
                JsonObject item = new JsonObject(); item.addProperty("kind", value.kind); item.addProperty("recorded_at", value.recordedAt);
                feedback.add(id, item);
            });
            request.add("recommendation_feedback", feedback);
            check.setEnabled(false); progressFresh = false;
            resultMessage = "Checking Python...";
            connection.setText("Checking Python...");
            if (snapshot == null) render();
            else publishFocus(); // Keep the last confirmed sidebar layout while refreshing.
            pending = PlannerClient.evaluateGoals(transport, gson, service.endpoint, request.toString());
            pending.whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
                if (closed || revision != requestedRevision) return;
                pending = null; check.setEnabled(true);
                if (liveRefreshQueued && isLive() && System.currentTimeMillis() >= nextRefreshAt) { liveRefreshQueued = false; evaluate(); return; }
                if (failure != null) { showFailure(failure); return; }
                snapshot = result; progressFresh = !liveRefreshQueued; retryAfter = 0; feedbackCheckedAt = requestedAt;
                connection.setText(liveRefreshQueued ? "Checking Python..." : "Connected to Python"); render(); recordHistory();
            }));
        } catch (Exception failure) {
            pending = null; check.setEnabled(true); showFailure(failure);
        }
    }

    private void showFailure(Throwable failure) {
        progressFresh = false;
        while (failure.getCause() != null) failure = failure.getCause();
        retryAfter = System.currentTimeMillis() + 5000;
        connection.setText("Service unavailable");
        resultMessage = "The planner could not refresh. Your saved choices are kept; displayed results may be out of date. Retry when the service is available.\n"
            + failure.getClass().getSimpleName() + ": " + (failure.getMessage() == null ? "Connection failed" : failure.getMessage());
        render();
    }

    private void updateServiceNotice() {
        serviceNotice.setText(service.disclosure());
        serviceStatus.setText(service.canSend() ? ("https".equals(service.endpoint.getScheme())
            ? "Hosted planner: " + service.endpoint.getHost() : "Local development planner")
            : "Planner off. Configure the service and sharing in plugin settings.");
        for (JTextArea label : new JTextArea[]{serviceNotice, serviceStatus}) {
            label.setPreferredSize(null);
            label.setSize(200, Short.MAX_VALUE);
            int height = label.getPreferredSize().height;
            label.setPreferredSize(new java.awt.Dimension(200, height));
            label.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, height));
        }
    }

    void configureService(ServiceSettings next) {
        if (closed) return;
        service = next; nextRefreshAt = 0;
        invalidateRequest();
        updateServiceNotice();
        if (isLive() && liveAccount != null) evaluate();
    }

    public void close() {
        closed = true; invalidateRequest();
        // Finish any already accepted atomic save; no new tasks are accepted after close.
        storageExecutor.shutdown();
    }

    String connectionText() { return connection.getText(); }
    int resultCount() { return rows.getComponentCount(); }
    void selectDemo(int index) { demo.setSelectedIndex(index); }
    void setPreferences(String[] interests, int commitmentIndex) {
        editingPreferences = true;
        try {
            java.util.List<String> selected = java.util.Arrays.asList(interests);
            combat.setSelected(selected.contains("combat"));
            quests.setSelected(selected.contains("quests"));
            exploration.setSelected(selected.contains("exploration"));
            utility.setSelected(selected.contains("utility"));
            commitment.setSelectedIndex(commitmentIndex);
        } finally { editingPreferences = false; }
        invalidateRequest();
        if (isLive()) evaluate();
    }
    private void resetScroll() {
        if (getScrollPane() != null) getScrollPane().getViewport().setViewPosition(new java.awt.Point());
    }
    void selectGoal(String id) {
        if (tabs.getSelectedIndex() == 0) tabs.setSelectedIndex(1);
        selectedGoal = id; resetScroll(); render();
    }
    void showFocus() { tabs.setSelectedIndex(0); resetScroll(); render(); }
    private void filterGoals() {
        if (tabs.getSelectedIndex() == 1) { selectedGoal = null; resetScroll(); render(); }
    }
    void searchGoals(String query, int access) { goalAccess.setSelectedIndex(access); goalSearch.setText(query); }
    void showBrowser() { tabs.setSelectedIndex(1); savedOnly = false; selectedGoal = null; resetScroll(); render(); }
    void showSaved() { tabs.setSelectedIndex(2); savedOnly = true; selectedGoal = null; resetScroll(); render(); }
    boolean selectionsReady() { return selectionsLoaded && !saving; }
    GoalSelection selectedGoals() { return currentSelection(); }
    String selectionText() { return collectText(selectionHeader); }
    String visibleText() { return collectText(tabs.getSelectedIndex() == 0 ? selectionHeader : rows); }
    private static String collectText(java.awt.Container parent) {
        StringBuilder result = new StringBuilder();
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JTextArea) result.append(((JTextArea) child).getText()).append('\n');
            else if (child instanceof java.awt.Container) result.append(collectText((java.awt.Container) child));
        }
        return result.toString();
    }
}
