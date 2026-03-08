package com.jagrosh.jmusicbot.gui;

import com.jagrosh.jmusicbot.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class PlaylistManagerPanel extends JPanel {
    private final Bot bot;
    private final List<ScopeEditorPanel> scopedPanels = new ArrayList<>();

    public PlaylistManagerPanel(Bot bot) {
        super(new BorderLayout(10, 10));
        this.bot = bot;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTabbedPane tabs = new JTabbedPane();
        ScopeEditorPanel playlistPanel = new ScopeEditorPanel(ScopeType.PLAYLIST);
        ScopeEditorPanel mylistPanel = new ScopeEditorPanel(ScopeType.MYLIST);
        ScopeEditorPanel publicPanel = new ScopeEditorPanel(ScopeType.PUBLIC);
        scopedPanels.add(playlistPanel);
        scopedPanels.add(mylistPanel);
        scopedPanels.add(publicPanel);

        tabs.addTab("プレイリスト", playlistPanel);
        tabs.addTab("マイリスト", mylistPanel);
        tabs.addTab("公開リスト", publicPanel);

        add(tabs, BorderLayout.CENTER);
    }

    public void onBotConnected() {
        for (ScopeEditorPanel panel : scopedPanels) {
            panel.handleBotConnected();
        }
    }

    private enum ScopeType {
        PLAYLIST,
        MYLIST,
        PUBLIC
    }

    private class ScopeEditorPanel extends JPanel {
        private final ScopeType scopeType;
        private final DefaultListModel<String> nameModel;
        private final JList<String> nameList;
        private final JTextArea editor;
        private final JLabel statusLabel;
        private final JProgressBar progressBar;
        private final JButton refreshButton;
        private final JButton createButton;
        private final JButton deleteButton;
        private final JButton saveButton;
        private final JLabel selectedLabel;
        private final JComboBox<TargetOption> targetCombo;
        private final JTextField targetSearchField;
        private final JButton clearSearchButton;
        private final JLabel selectedTargetLabel;
        private final List<TargetOption> allTargetOptions = new ArrayList<>();
        private final Map<String, String> resolvedUserNameCache = new HashMap<>();

        private boolean updatingList;
        private boolean updatingTargetCombo;

        ScopeEditorPanel(ScopeType scopeType) {
            super(new BorderLayout(10, 10));
            this.scopeType = scopeType;
            this.nameModel = new DefaultListModel<>();
            this.nameList = new JList<>(nameModel);
            this.editor = new JTextArea();
            this.statusLabel = new JLabel("準備完了");
            this.progressBar = new JProgressBar();
            this.refreshButton = new JButton(scopeListLoadButtonLabel(scopeType));
            this.createButton = new JButton("新規作成");
            this.deleteButton = new JButton("削除");
            this.saveButton = new JButton("保存");
            this.selectedLabel = new JLabel("未選択");
            this.targetCombo = new JComboBox<>();
            this.targetSearchField = new JTextField(16);
            this.clearSearchButton = new JButton("条件クリア");
            this.selectedTargetLabel = new JLabel("対象未選択");

            buildUI();
            installListeners();
            if (scopeType == ScopeType.PUBLIC) {
                refreshNamesAsync();
            } else {
                statusLabel.setText("Bot接続完了後に対象候補を読み込みます。");
            }
        }

        private void buildUI() {
            JPanel top = new JPanel(new BorderLayout(8, 8));
            top.add(createScopeHeader(), BorderLayout.NORTH);
            top.add(createToolbar(), BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createListPane(), createEditorPane());
            splitPane.setResizeWeight(0.32);

            JPanel bottom = new JPanel(new BorderLayout(8, 8));
            progressBar.setIndeterminate(true);
            progressBar.setVisible(false);
            progressBar.setStringPainted(true);
            progressBar.setString("処理中...");
            progressBar.setPreferredSize(new Dimension(220, 28));
            bottom.add(statusLabel, BorderLayout.WEST);
            bottom.add(progressBar, BorderLayout.EAST);

            add(top, BorderLayout.NORTH);
            add(splitPane, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);
        }

        private JPanel createScopeHeader() {
            JPanel header = new JPanel(new BorderLayout(8, 8));
            header.setBorder(createSectionBorder());
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            switch (scopeType) {
                case PLAYLIST -> {
                    controls.add(new JLabel("対象サーバー"));
                    targetCombo.setEditable(false);
                    targetCombo.setPreferredSize(new Dimension(380, 40));
                    targetCombo.setFont(targetCombo.getFont().deriveFont(Font.PLAIN, 14f));
                    targetCombo.setMaximumRowCount(18);
                    targetCombo.setRenderer(createTargetRenderer());
                    controls.add(targetCombo);
                    controls.add(new JLabel("検索"));
                    styleFilterField(targetSearchField);
                    controls.add(targetSearchField);
                    styleActionButton(clearSearchButton);
                    controls.add(clearSearchButton);
                    JButton loadGuilds = new JButton("接続サーバー候補を読込");
                    styleActionButton(loadGuilds);
                    loadGuilds.addActionListener(e -> refreshGuildCandidates());
                    controls.add(loadGuilds);
                }
                case MYLIST -> {
                    controls.add(new JLabel("対象ユーザー"));
                    targetCombo.setEditable(false);
                    targetCombo.setPreferredSize(new Dimension(380, 40));
                    targetCombo.setFont(targetCombo.getFont().deriveFont(Font.PLAIN, 14f));
                    targetCombo.setMaximumRowCount(18);
                    targetCombo.setRenderer(createTargetRenderer());
                    controls.add(targetCombo);
                    controls.add(new JLabel("検索"));
                    styleFilterField(targetSearchField);
                    controls.add(targetSearchField);
                    styleActionButton(clearSearchButton);
                    controls.add(clearSearchButton);
                    JButton loadUsers = new JButton("ユーザーリスト読込");
                    styleActionButton(loadUsers);
                    loadUsers.addActionListener(e -> refreshUserCandidatesAsync());
                    controls.add(loadUsers);
                }
                case PUBLIC -> controls.add(new JLabel("公開リストは全体共有です"));
            }
            selectedTargetLabel.setFont(selectedTargetLabel.getFont().deriveFont(Font.PLAIN, 13f));
            selectedTargetLabel.setForeground(new Color(80, 80, 80));
            selectedTargetLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
            header.add(controls, BorderLayout.NORTH);
            if (scopeType != ScopeType.PUBLIC) {
                header.add(selectedTargetLabel, BorderLayout.SOUTH);
            }
            return header;
        }

        private JPanel createToolbar() {
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            toolbar.setBorder(createSectionBorder());
            styleActionButton(refreshButton);
            styleActionButton(createButton);
            styleActionButton(deleteButton);
            toolbar.add(refreshButton);
            toolbar.add(createButton);
            toolbar.add(deleteButton);
            return toolbar;
        }

        private JPanel createListPane() {
            JPanel pane = new JPanel(new BorderLayout(6, 6));
            pane.setBorder(createSectionBorder());
            nameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            nameList.setFont(nameList.getFont().deriveFont(Font.PLAIN, 14f));
            pane.add(new JScrollPane(nameList), BorderLayout.CENTER);
            return pane;
        }

        private JPanel createEditorPane() {
            JPanel pane = new JPanel(new BorderLayout(6, 6));
            pane.setBorder(createSectionBorder());

            JPanel top = new JPanel(new BorderLayout(8, 0));
            selectedLabel.setFont(selectedLabel.getFont().deriveFont(Font.BOLD, 14f));
            stylePrimaryButton(saveButton);
            top.add(new JLabel("編集中"), BorderLayout.WEST);
            top.add(selectedLabel, BorderLayout.CENTER);
            top.add(saveButton, BorderLayout.EAST);

            editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            editor.setTabSize(2);
            editor.setLineWrap(false);
            editor.setWrapStyleWord(false);
            editor.setText("# 1行に1つ、URLまたは検索クエリを記述\n# 先頭に #shuffle を書くとシャッフル読み込み\n");

            pane.add(top, BorderLayout.NORTH);
            pane.add(new JScrollPane(editor), BorderLayout.CENTER);
            return pane;
        }

        private void installListeners() {
            refreshButton.addActionListener(e -> refreshNamesAsync());
            createButton.addActionListener(e -> createPlaylistAsync());
            deleteButton.addActionListener(e -> deleteSelectedAsync());
            saveButton.addActionListener(e -> saveSelectedAsync());
            nameList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && !updatingList) {
                    loadSelectedAsync();
                }
            });
            if (scopeType != ScopeType.PUBLIC) {
                targetCombo.addActionListener((ActionEvent e) -> {
                    if (!updatingTargetCombo) {
                        updateSelectedTargetLabel();
                        refreshNamesAsync();
                    }
                });
                clearSearchButton.addActionListener(e -> {
                    targetSearchField.setText("");
                    applyTargetFilter();
                });
                targetSearchField.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        applyTargetFilter();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        applyTargetFilter();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        applyTargetFilter();
                    }
                });
            }
        }

        private void handleBotConnected() {
            if (scopeType == ScopeType.PLAYLIST) {
                refreshGuildCandidates();
                refreshNamesAsync();
            } else if (scopeType == ScopeType.MYLIST) {
                refreshUserCandidatesAsync();
            } else {
                refreshNamesAsync();
            }
        }

        private void refreshGuildCandidates() {
            List<TargetOption> candidates = new ArrayList<>();
            if (bot.getJDA() != null) {
                for (Guild guild : bot.getJDA().getGuilds()) {
                    candidates.add(new TargetOption(guild.getId(), guild.getName(), false));
                }
            }
            candidates.sort(Comparator.comparing(TargetOption::label, String.CASE_INSENSITIVE_ORDER));
            allTargetOptions.clear();
            allTargetOptions.addAll(candidates);
            applyTargetFilter();
            if (!allTargetOptions.isEmpty()) {
                statusLabel.setText("対象サーバーを選択してください。");
            } else {
                statusLabel.setText("参加中のサーバーが見つかりません。");
            }
            updateSelectedTargetLabel();
        }

        private void refreshUserCandidatesAsync() {
            setLoading(true, "ユーザーリストを読み込み中...");
            new SwingWorker<List<TargetOption>, Void>() {
                private String ownerId;

                @Override
                protected List<TargetOption> doInBackground() {
                    ownerId = String.valueOf(bot.getConfig().getOwnerId());
                    return loadUserTargetOptions(ownerId);
                }

                @Override
                protected void done() {
                    boolean shouldLoadNames = false;
                    try {
                        List<TargetOption> options = get();
                        allTargetOptions.clear();
                        allTargetOptions.addAll(options);
                        applyTargetFilter();

                        if (!allTargetOptions.isEmpty()) {
                            TargetOption ownerOption = allTargetOptions.stream()
                                    .filter(o -> o.id().equals(ownerId))
                                    .findFirst()
                                    .orElse(allTargetOptions.get(0));
                            targetCombo.setSelectedItem(ownerOption);
                            statusLabel.setText("対象ユーザーを選択してください。");
                            shouldLoadNames = true;
                        } else {
                            statusLabel.setText("ユーザー候補が見つかりません。");
                        }
                        updateSelectedTargetLabel();
                    } catch (Exception ex) {
                        statusLabel.setText("ユーザー候補の読み込みに失敗: " + ex.getMessage());
                    } finally {
                        setLoading(false, null);
                        if (shouldLoadNames) {
                            refreshNamesAsync();
                        }
                    }
                }
            }.execute();
        }

        private List<TargetOption> loadUserTargetOptions(String ownerId) {
            Set<String> mylistOwnerIds = getExistingMylistOwnerIds();
            Map<String, TargetOption> optionsById = new LinkedHashMap<>();

            // 1) 既存マイリスト保有者を最優先で候補化（Discordキャッシュ不足時でも候補を維持）
            for (String userId : mylistOwnerIds) {
                optionsById.put(userId, new TargetOption(userId, formatUserLabel(userId, null, true), true));
            }

            // 2) Discordキャッシュ済みユーザーを統合
            if (bot.getJDA() != null) {
                for (User user : bot.getJDA().getUsers()) {
                    boolean hasMylist = mylistOwnerIds.contains(user.getId());
                    resolvedUserNameCache.put(user.getId(), user.getName());
                    optionsById.put(user.getId(), new TargetOption(
                            user.getId(),
                            formatUserLabel(user.getId(), user.getName(), hasMylist),
                            hasMylist
                    ));
                }
            }

            // 3) API補完: キャッシュにいないIDのユーザー名を取得（マイリスト保有者 + Owner）
            Set<String> unresolvedIds = new HashSet<>(mylistOwnerIds);
            unresolvedIds.add(ownerId);
            unresolvedIds.removeIf(optionsById::containsKey);
            if (bot.getJDA() != null) {
                for (String unresolvedId : unresolvedIds) {
                    String resolved = resolvedUserNameCache.get(unresolvedId);
                    if (resolved == null) {
                        try {
                            User user = bot.getJDA().retrieveUserById(unresolvedId).complete();
                            if (user != null) {
                                resolved = user.getName();
                                resolvedUserNameCache.put(unresolvedId, resolved);
                            }
                        } catch (Exception ignored) {
                            // 取得できない場合はIDベース表示で継続
                        }
                    }
                    boolean hasMylist = mylistOwnerIds.contains(unresolvedId);
                    optionsById.put(unresolvedId, new TargetOption(
                            unresolvedId,
                            formatUserLabel(unresolvedId, resolved, hasMylist),
                            hasMylist
                    ));
                }
            }

            // 4) Ownerは必ず候補に残す
            if (!optionsById.containsKey(ownerId)) {
                boolean hasMylist = mylistOwnerIds.contains(ownerId);
                String ownerName = resolvedUserNameCache.get(ownerId);
                optionsById.put(ownerId, new TargetOption(
                        ownerId,
                        formatUserLabel(ownerId, ownerName, hasMylist),
                        hasMylist
                ));
            }

            List<TargetOption> options = new ArrayList<>();
            options.addAll(optionsById.values());
            options.sort(
                    Comparator.comparing(TargetOption::hasMylist).reversed()
                            .thenComparing(TargetOption::label, String.CASE_INSENSITIVE_ORDER)
            );
            return options;
        }

        private void applyTargetFilter() {
            if (scopeType == ScopeType.PUBLIC) {
                return;
            }
            String filter = targetSearchField.getText() == null ? "" : targetSearchField.getText().trim().toLowerCase();
            TargetOption selected = (TargetOption) targetCombo.getSelectedItem();
            updatingTargetCombo = true;
            try {
                targetCombo.removeAllItems();
                for (TargetOption option : allTargetOptions) {
                    if (filter.isEmpty() || option.label().toLowerCase().contains(filter)) {
                        targetCombo.addItem(option);
                    }
                }
                if (targetCombo.getItemCount() == 0) {
                    statusLabel.setText("検索条件に一致する対象がありません。");
                    updateSelectedTargetLabel();
                    return;
                }
                if (selected != null) {
                    for (int i = 0; i < targetCombo.getItemCount(); i++) {
                        TargetOption option = targetCombo.getItemAt(i);
                        if (option.id().equals(selected.id())) {
                            targetCombo.setSelectedItem(option);
                            return;
                        }
                    }
                }
                targetCombo.setSelectedIndex(0);
            } finally {
                updatingTargetCombo = false;
            }
            updateSelectedTargetLabel();
        }

        private String currentTargetIdOrEmpty() {
            if (scopeType == ScopeType.PUBLIC) {
                return "";
            }
            TargetOption selected = (TargetOption) targetCombo.getSelectedItem();
            if (selected == null) {
                return "";
            }
            return selected.id();
        }

        private void refreshNamesAsync() {
            if (!validateTargetIfNeeded()) {
                return;
            }
            setLoading(true, "一覧を読み込み中...");
            new SwingWorker<List<String>, Void>() {
                @Override
                protected List<String> doInBackground() {
                    return listNames();
                }

                @Override
                protected void done() {
                    try {
                        List<String> names = get();
                        applyNameList(names);
                        statusLabel.setText("一覧を更新しました: " + names.size() + "件");
                    } catch (Exception ex) {
                        statusLabel.setText("一覧の更新に失敗: " + ex.getMessage());
                    } finally {
                        setLoading(false, null);
                    }
                }
            }.execute();
        }

        private List<String> listNames() {
            String targetId = currentTargetIdOrEmpty();
            List<String> names;
            switch (scopeType) {
                case PLAYLIST -> names = bot.getPlaylistLoader().getPlaylistNames(targetId);
                case MYLIST -> names = bot.getMylistLoader().getPlaylistNames(targetId);
                case PUBLIC -> names = bot.getPublistLoader().getPlaylistNames();
                default -> names = Collections.emptyList();
            }
            names.sort(String::compareToIgnoreCase);
            return names;
        }

        private void applyNameList(List<String> names) {
            boolean shouldLoadSelected = false;
            updatingList = true;
            try {
                String prev = nameList.getSelectedValue();
                nameModel.clear();
                names.forEach(nameModel::addElement);
                if (prev != null && names.contains(prev)) {
                    nameList.setSelectedValue(prev, true);
                    shouldLoadSelected = true;
                } else if (!names.isEmpty()) {
                    nameList.setSelectedIndex(0);
                    shouldLoadSelected = true;
                } else {
                    selectedLabel.setText("未選択");
                    editor.setText("");
                }
            } finally {
                updatingList = false;
            }
            if (shouldLoadSelected && nameList.getSelectedValue() != null) {
                SwingUtilities.invokeLater(this::loadSelectedAsync);
            }
        }

        private void loadSelectedAsync() {
            String name = nameList.getSelectedValue();
            if (name == null) {
                selectedLabel.setText("未選択");
                return;
            }
            setLoading(true, "内容を読み込み中...");
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    Path path = filePathOf(name);
                    if (Files.exists(path)) {
                        return Files.readString(path, StandardCharsets.UTF_8);
                    }
                    return "";
                }

                @Override
                protected void done() {
                    try {
                        editor.setText(get());
                        editor.setCaretPosition(0);
                        selectedLabel.setText(name);
                        statusLabel.setText("内容を読み込みました: " + name);
                    } catch (Exception ex) {
                        statusLabel.setText("読み込みに失敗: " + ex.getMessage());
                    } finally {
                        setLoading(false, null);
                    }
                }
            }.execute();
        }

        private void createPlaylistAsync() {
            if (!validateTargetIfNeeded()) {
                return;
            }
            String name = JOptionPane.showInputDialog(this, "新しいリスト名を入力してください");
            if (name == null) {
                return;
            }
            String normalized = normalizeName(name);
            if (normalized.isEmpty()) {
                JOptionPane.showMessageDialog(this, "有効なリスト名を入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
            setLoading(true, "新規作成中...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    String targetId = currentTargetIdOrEmpty();
                    switch (scopeType) {
                        case PLAYLIST -> bot.getPlaylistLoader().createPlaylist(targetId, normalized);
                        case MYLIST -> bot.getMylistLoader().createPlaylist(targetId, normalized);
                        case PUBLIC -> bot.getPublistLoader().createPlaylist(normalized);
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        statusLabel.setText("作成しました: " + normalized);
                        refreshNamesAsync();
                        SwingUtilities.invokeLater(() -> nameList.setSelectedValue(normalized, true));
                    } catch (Exception ex) {
                        statusLabel.setText("作成に失敗: " + ex.getMessage());
                    } finally {
                        setLoading(false, null);
                    }
                }
            }.execute();
        }

        private void deleteSelectedAsync() {
            String name = nameList.getSelectedValue();
            if (name == null) {
                JOptionPane.showMessageDialog(this, "削除するリストを選択してください。", "未選択", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int choice = JOptionPane.showConfirmDialog(this, "「" + name + "」を削除します。よろしいですか？", "リスト削除", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            setLoading(true, "削除中...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    String targetId = currentTargetIdOrEmpty();
                    switch (scopeType) {
                        case PLAYLIST -> bot.getPlaylistLoader().deletePlaylist(targetId, name);
                        case MYLIST -> bot.getMylistLoader().deletePlaylist(targetId, name);
                        case PUBLIC -> bot.getPublistLoader().deletePlaylist(name);
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        statusLabel.setText("削除しました: " + name);
                        refreshNamesAsync();
                    } catch (Exception ex) {
                        statusLabel.setText("削除に失敗: " + ex.getMessage());
                    } finally {
                        setLoading(false, null);
                    }
                }
            }.execute();
        }

        private void saveSelectedAsync() {
            String name = nameList.getSelectedValue();
            if (name == null) {
                JOptionPane.showMessageDialog(this, "保存するリストを選択してください。", "未選択", JOptionPane.WARNING_MESSAGE);
                return;
            }
            setLoading(true, "保存中...");
            String text = editor.getText();
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    String targetId = currentTargetIdOrEmpty();
                    switch (scopeType) {
                        case PLAYLIST -> bot.getPlaylistLoader().writePlaylist(targetId, name, text);
                        case MYLIST -> bot.getMylistLoader().writePlaylist(targetId, name, text);
                        case PUBLIC -> bot.getPublistLoader().writePlaylist(name, text);
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        statusLabel.setText("保存しました: " + name);
                    } catch (Exception ex) {
                        statusLabel.setText("保存に失敗: " + ex.getMessage());
                    } finally {
                        setLoading(false, null);
                    }
                }
            }.execute();
        }

        private Path filePathOf(String name) {
            String targetId = currentTargetIdOrEmpty();
            return switch (scopeType) {
                case PLAYLIST -> Paths.get(bot.getConfig().getPlaylistsFolder(), targetId, name + ".txt");
                case MYLIST -> Paths.get(bot.getConfig().getMylistfolder(), targetId, name + ".txt");
                case PUBLIC -> Paths.get(bot.getConfig().getPublistFolder(), name + ".txt");
            };
        }

        private String normalizeName(String input) {
            String trimmed = input == null ? "" : input.trim();
            if (trimmed.isEmpty()) {
                return "";
            }
            return trimmed.replace("/", "_").replace("\\", "_").replace(":", "_");
        }

        private boolean validateTargetIfNeeded() {
            if (scopeType == ScopeType.PUBLIC) {
                return true;
            }
            String targetId = currentTargetIdOrEmpty();
            if (targetId.isBlank()) {
                if (scopeType == ScopeType.PLAYLIST) {
                    statusLabel.setText("参加中サーバーがないため、対象を選択できません。");
                } else {
                    statusLabel.setText("対象ユーザー候補がないため、対象を選択できません。");
                }
                return false;
            }
            return true;
        }

        private void setLoading(boolean loading, String status) {
            progressBar.setVisible(loading);
            refreshButton.setEnabled(!loading);
            createButton.setEnabled(!loading);
            deleteButton.setEnabled(!loading);
            saveButton.setEnabled(!loading);
            nameList.setEnabled(!loading);
            editor.setEnabled(!loading);
            if (scopeType != ScopeType.PUBLIC) {
                targetCombo.setEnabled(!loading);
            }
            if (status != null) {
                statusLabel.setText(status);
            }
        }

        private void styleActionButton(AbstractButton button) {
            button.setFont(button.getFont().deriveFont(Font.PLAIN, 14f));
            button.setMargin(new Insets(10, 14, 10, 14));
            button.setPreferredSize(new Dimension(Math.max(button.getPreferredSize().width, 130), 40));
        }

        private void stylePrimaryButton(AbstractButton button) {
            styleActionButton(button);
            button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        }

        private void styleFilterField(JTextField field) {
            field.setFont(field.getFont().deriveFont(Font.PLAIN, 14f));
            field.setPreferredSize(new Dimension(220, 40));
            field.setToolTipText("サーバー名/ユーザー名で絞り込み");
        }

        private void updateSelectedTargetLabel() {
            if (scopeType == ScopeType.PUBLIC) {
                return;
            }
            TargetOption selected = (TargetOption) targetCombo.getSelectedItem();
            if (selected == null) {
                selectedTargetLabel.setText("対象未選択");
                return;
            }
            selectedTargetLabel.setText("選択中: " + selected.label() + "  (ID: " + selected.id() + ")");
        }

        private ListCellRenderer<? super TargetOption> createTargetRenderer() {
            return new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof TargetOption option) {
                        if (index < 0) {
                            label.setText(option.label());
                            label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
                        } else {
                            label.setText(option.label() + "  [" + option.id() + "]");
                            label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
                        }
                    }
                    label.setBorder(new EmptyBorder(6, 8, 6, 8));
                    return label;
                }
            };
        }

        private Border createSectionBorder() {
            return BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 224, 230)),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            );
        }
    }

    private String formatUserLabel(String id, String userName, boolean hasMylist) {
        String base = (userName == null || userName.isBlank()) ? ("User " + id) : userName;
        return hasMylist ? (base + "  [マイリストあり]") : base;
    }

    private Set<String> getExistingMylistOwnerIds() {
        Set<String> ids = new HashSet<>();
        Path root = Paths.get(bot.getConfig().getMylistfolder());
        if (!Files.isDirectory(root)) {
            return ids;
        }
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(Files::isDirectory)
                    .filter(this::hasPlaylistFiles)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d{5,30}"))
                    .forEach(ids::add);
        } catch (Exception ignored) {
            // フォルダ走査失敗時はDiscordキャッシュのみで継続
        }
        return ids;
    }

    private boolean hasPlaylistFiles(Path ownerDir) {
        try (Stream<Path> files = Files.list(ownerDir)) {
            return files.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".txt"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String scopeListLoadButtonLabel(ScopeType scopeType) {
        return switch (scopeType) {
            case PLAYLIST -> "プレイリスト一覧読込";
            case MYLIST -> "マイリスト一覧読込";
            case PUBLIC -> "公開リスト一覧読込";
        };
    }

    private record TargetOption(String id, String label, boolean hasMylist) {
        @Override
        public String toString() {
            return label;
        }
    }
}
