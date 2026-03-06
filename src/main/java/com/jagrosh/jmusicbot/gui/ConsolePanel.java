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
    private final Highlighter.HighlightPainter highlightPainter;
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
        highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 232, 168));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel toolbar = new JPanel(new BorderLayout(8, 8));
        toolbar.add(createActionBar(), BorderLayout.NORTH);
        toolbar.add(createSearchBar(), BorderLayout.SOUTH);

        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        installListeners();
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

    private JPanel createActionBar() {
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        JButton clear = new JButton("クリア");
        JButton copy = new JButton("全てコピー");

        pauseButton.addActionListener(e -> {
            outputStream.setPaused(pauseButton.isSelected());
            pauseButton.setText(pauseButton.isSelected() ? "再開" : "一時停止");
        });
        clear.addActionListener(e -> clearConsole());
        copy.addActionListener(e -> copyAllLogs());

        actionBar.add(pauseButton);
        actionBar.add(clear);
        actionBar.add(copy);
        actionBar.add(autoScroll);
        return actionBar;
    }

    private JPanel createSearchBar() {
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel label = new JLabel("検索");
        JButton prev = new JButton("前");
        JButton next = new JButton("次");

        prev.addActionListener(e -> selectRelativeMatch(-1));
        next.addActionListener(e -> selectRelativeMatch(1));

        searchBar.add(label);
        searchBar.add(searchField);
        searchBar.add(prev);
        searchBar.add(next);
        searchBar.add(matchCountLabel);
        return searchBar;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        status.add(lineCountLabel);
        status.add(charCountLabel);
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
            matchCountLabel.setText("0件");
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

        matchCountLabel.setText(matches.size() + "件");
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
        matchCountLabel.setText((selectedMatch + 1) + "/" + matches.size());
    }
}
