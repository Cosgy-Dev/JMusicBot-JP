/*
 * Copyright 2018-2020 Cosgy Dev
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jagrosh.jmusicbot.gui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class ConsolePanel extends JPanel {

    private final JTextArea textArea;
    private final TextAreaOutputStream outputStream;
    private final JCheckBox autoScroll;
    private final JToggleButton pauseButton;
    private final JTextField searchField;
    private final JLabel matchCountLabel;
    private final JLabel lineCountLabel;
    private final JLabel charCountLabel;
    private final JProgressBar renderProgressBar;
    private final JLabel backlogCountLabel;
    private Highlighter.HighlightPainter highlightPainter;
    private final List<int[]> matches = new ArrayList<>();
    private int selectedMatch = -1;

    public ConsolePanel() {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textArea.setTabSize(2);

        outputStream = new TextAreaOutputStream(textArea, 5000);
        PrintStream con = new PrintStream(outputStream, true, StandardCharsets.UTF_8);
        System.setOut(con);
        System.setErr(con);

        autoScroll = new JCheckBox("自動スクロール", true);
        pauseButton = new JToggleButton("一時停止");
        searchField = new JTextField(28);
        matchCountLabel = new JLabel("0件");
        lineCountLabel = new JLabel();
        charCountLabel = new JLabel();
        backlogCountLabel = new JLabel("描画待ち: 0");
        renderProgressBar = new JProgressBar();
        renderProgressBar.setIndeterminate(true);
        renderProgressBar.setVisible(false);
        renderProgressBar.setStringPainted(true);
        renderProgressBar.setString("非同期描画中...");
        highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(getHighlightColor());

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel toolbar = new JPanel(new BorderLayout(8, 8));
        toolbar.add(createActionBar(), BorderLayout.NORTH);
        toolbar.add(createSearchBar(), BorderLayout.SOUTH);

        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        installListeners();
        outputStream.setBacklogListener(this::updateBacklogProgress);
        updatePauseButtonState();
        updateMatchCountLabel();
        updateStats();
    }

    public void clearConsole() {
        outputStream.clear();
        refreshSearchHighlights();
        updateStats();
    }

    public void copyAllLogs() {
        StringSelection content = new StringSelection(textArea.getText());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(content, null);
    }

    public boolean isPaused() {
        return outputStream.isPaused();
    }

    public int getLogLineCount() {
        return Math.max(textArea.getLineCount(), 0);
    }

    public void applyTheme() {
        highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(getHighlightColor());
        if (!searchField.getText().isBlank()) {
            refreshSearchHighlights();
        }
        updatePauseButtonState();
    }

    private JPanel createActionBar() {
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        JButton clear = createActionButton("クリア", "Ctrl+L");
        JButton copy = createActionButton("コピー", "Ctrl+Shift+C");
        styleToggleButton(pauseButton);
        pauseButton.setToolTipText("ログ描画を一時停止/再開します");
        pauseButton.addActionListener(e -> {
            outputStream.setPaused(pauseButton.isSelected());
            updatePauseButtonState();
        });

        autoScroll.setFont(autoScroll.getFont().deriveFont(Font.PLAIN, 14f));
        autoScroll.setToolTipText("新しいログ受信時に末尾へ自動移動します");

        clear.setToolTipText("コンソールを消去します");
        copy.setToolTipText("現在のログをクリップボードへコピーします");
        clear.addActionListener(e -> clearConsoleWithConfirm());
        copy.addActionListener(e -> copyAllLogs());

        actionBar.add(pauseButton);
        actionBar.add(clear);
        actionBar.add(copy);
        actionBar.add(Box.createHorizontalStrut(8));
        actionBar.add(autoScroll);
        return actionBar;
    }

    private JPanel createSearchBar() {
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchBar.setBorder(BorderFactory.createTitledBorder("ログ検索"));

        JLabel label = new JLabel("キーワード");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
        searchField.setFont(searchField.getFont().deriveFont(Font.PLAIN, 14f));
        searchField.setColumns(32);
        searchField.setToolTipText("Enter: 次 / Shift+Enter: 前 / Esc: 検索クリア");

        JButton prev = createActionButton("前へ", "Shift+Enter");
        JButton next = createActionButton("次へ", "Enter");
        JButton clearSearch = createActionButton("検索クリア", "Esc");
        matchCountLabel.setFont(matchCountLabel.getFont().deriveFont(Font.BOLD, 14f));

        prev.addActionListener(e -> selectRelativeMatch(-1));
        next.addActionListener(e -> selectRelativeMatch(1));
        clearSearch.addActionListener(e -> clearSearchInput());

        searchBar.add(label);
        searchBar.add(searchField);
        searchBar.add(prev);
        searchBar.add(next);
        searchBar.add(clearSearch);
        searchBar.add(matchCountLabel);
        return searchBar;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout(10, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        left.add(lineCountLabel);
        left.add(charCountLabel);
        left.add(backlogCountLabel);

        JPanel right = new JPanel(new BorderLayout());
        right.add(renderProgressBar, BorderLayout.CENTER);
        right.setPreferredSize(new Dimension(240, 24));

        status.add(left, BorderLayout.WEST);
        status.add(right, BorderLayout.EAST);
        return status;
    }

    private void installListeners() {
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                afterTextChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                afterTextChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                afterTextChanged();
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshSearchHighlights();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshSearchHighlights();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshSearchHighlights();
            }
        });

        textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control F"), "focusSearch");
        textArea.getActionMap().put("focusSearch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });

        textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control L"), "clearConsole");
        textArea.getActionMap().put("clearConsole", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clearConsoleWithConfirm();
            }
        });

        textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ctrl shift C"), "copyConsole");
        textArea.getActionMap().put("copyConsole", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                copyAllLogs();
            }
        });

        textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control P"), "togglePause");
        textArea.getActionMap().put("togglePause", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                pauseButton.doClick();
            }
        });

        searchField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("ENTER"), "searchNext");
        searchField.getActionMap().put("searchNext", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                selectRelativeMatch(1);
            }
        });

        searchField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("shift ENTER"), "searchPrev");
        searchField.getActionMap().put("searchPrev", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                selectRelativeMatch(-1);
            }
        });

        searchField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "clearSearch");
        searchField.getActionMap().put("clearSearch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clearSearchInput();
            }
        });
    }

    private void afterTextChanged() {
        updateStats();
        if (autoScroll.isSelected() && !isPaused()) {
            SwingUtilities.invokeLater(() -> textArea.setCaretPosition(textArea.getDocument().getLength()));
        }
        if (!searchField.getText().isBlank()) {
            refreshSearchHighlights();
        }
    }

    private void updateStats() {
        lineCountLabel.setText("行: " + getLogLineCount());
        charCountLabel.setText("文字: " + textArea.getDocument().getLength());
    }

    private void refreshSearchHighlights() {
        textArea.getHighlighter().removeAllHighlights();
        matches.clear();
        selectedMatch = -1;

        String needle = searchField.getText();
        if (needle == null || needle.isBlank()) {
            updateMatchCountLabel();
            return;
        }

        String haystack = textArea.getText().toLowerCase();
        String lowerNeedle = needle.toLowerCase();

        int index = 0;
        while ((index = haystack.indexOf(lowerNeedle, index)) >= 0) {
            int end = index + lowerNeedle.length();
            matches.add(new int[]{index, end});
            try {
                textArea.getHighlighter().addHighlight(index, end, highlightPainter);
            } catch (BadLocationException ignored) {
                // highlighting only affects UX; failures can be safely ignored
            }
            index = end;
        }

        updateMatchCountLabel();
        if (!matches.isEmpty()) {
            selectedMatch = 0;
            focusCurrentMatch();
        }
    }

    private void selectRelativeMatch(int delta) {
        if (matches.isEmpty()) {
            return;
        }
        selectedMatch = (selectedMatch + delta + matches.size()) % matches.size();
        focusCurrentMatch();
    }

    private void focusCurrentMatch() {
        if (selectedMatch < 0 || selectedMatch >= matches.size()) {
            return;
        }
        int[] range = matches.get(selectedMatch);
        textArea.requestFocusInWindow();
        textArea.select(range[0], range[1]);
        try {
            Rectangle view = textArea.modelToView2D(range[0]).getBounds();
            textArea.scrollRectToVisible(view);
        } catch (BadLocationException ignored) {
            // no-op
        }
        updateMatchCountLabel();
    }

    private void updateBacklogProgress(int pendingMessages) {
        backlogCountLabel.setText("描画待ち: " + pendingMessages);
        boolean loading = pendingMessages > 0;
        renderProgressBar.setVisible(loading);
        if (loading) {
            renderProgressBar.setString("非同期描画中... " + pendingMessages + "件");
        }
    }

    private JButton createActionButton(String label, String shortcutText) {
        JButton button = new JButton(label + "  " + shortcutText);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 14f));
        button.setMargin(new Insets(8, 12, 8, 12));
        return button;
    }

    private void styleToggleButton(JToggleButton button) {
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setMargin(new Insets(8, 12, 8, 12));
    }

    private void updatePauseButtonState() {
        if (pauseButton.isSelected()) {
            pauseButton.setText("再開  Ctrl+P");
            pauseButton.setForeground(new Color(133, 32, 32));
        } else {
            pauseButton.setText("一時停止  Ctrl+P");
            pauseButton.setForeground(UIManager.getColor("Button.foreground"));
        }
    }

    private void clearConsoleWithConfirm() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "コンソールをクリアします。よろしいですか？",
                "コンソールクリア",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice == JOptionPane.OK_OPTION) {
            clearConsole();
        }
    }

    private void clearSearchInput() {
        searchField.setText("");
        searchField.requestFocusInWindow();
        updateMatchCountLabel();
    }

    private void updateMatchCountLabel() {
        if (matches.isEmpty()) {
            matchCountLabel.setText("一致: 0件");
            return;
        }
        if (selectedMatch >= 0 && selectedMatch < matches.size()) {
            matchCountLabel.setText("一致: " + matches.size() + "件  (" + (selectedMatch + 1) + "/" + matches.size() + ")");
            return;
        }
        matchCountLabel.setText("一致: " + matches.size() + "件");
    }

    private Color getHighlightColor() {
        Color c = UIManager.getColor("TextArea.selectionBackground");
        if (c != null) {
            return c;
        }
        return new Color(255, 232, 168);
    }
}
