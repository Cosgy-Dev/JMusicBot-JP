/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.gui;

import com.formdev.flatlaf.FlatLightLaf;
import com.jagrosh.jmusicbot.Bot;
import net.dv8tion.jda.api.JDA;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.time.Instant;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class GUI extends JFrame {
    private final ConsolePanel console;
    private final PlaylistManagerPanel playlistManager;
    private final Bot bot;
    private final Instant startedAt;

    private final JLabel botStatusValue;
    private final JLabel uptimeValue;
    private final JLabel guildCountValue;
    private final JLabel pingValue;
    private final JLabel memoryValue;
    private final JLabel logLineValue;
    private final JLabel ffmpegValue;
    private final JLabel ytDlpVersionValue;
    private final JLabel statusBadge;
    private Instant lastExternalToolsRefreshedAt;
    private boolean listTargetsLoadedAfterConnect;

    public GUI(Bot bot) {
        super();
        this.bot = bot;
        this.console = new ConsolePanel();
        this.playlistManager = new PlaylistManagerPanel(bot);
        this.startedAt = Instant.now();

        this.botStatusValue = new JLabel("初期化中");
        this.uptimeValue = new JLabel("00:00:00");
        this.guildCountValue = new JLabel("0");
        this.pingValue = new JLabel("-");
        this.memoryValue = new JLabel("-");
        this.logLineValue = new JLabel("0");
        this.ffmpegValue = new JLabel("確認中");
        this.ytDlpVersionValue = new JLabel("確認中");
        this.statusBadge = new JLabel("初期化中");
        this.lastExternalToolsRefreshedAt = Instant.EPOCH;
        this.listTargetsLoadedAfterConnect = false;
    }

    public void init() {
        installLookAndFeel();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("JMusicBot JP");
        setMinimumSize(new Dimension(980, 640));

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(246, 248, 251));
        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createMainTabs(), BorderLayout.CENTER);

        setContentPane(root);

        Timer refreshTimer = new Timer(1000, e -> refreshStatus());
        refreshTimer.start();
        refreshStatus();

        pack();
        setSize(1100, 720);
        setLocationRelativeTo(null);
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    bot.shutdown();
                } catch (Exception ex) {
                    System.exit(0);
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                refreshTimer.stop();
            }
        });
    }

    private void installLookAndFeel() {
        try {
            FlatLightLaf.setup();
            UIManager.put("Component.arc", 14);
            UIManager.put("Button.arc", 14);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.showButtons", true);
        } catch (Exception ignored) {
            // FlatLafを適用できない場合は既定のL&Fを使用
        }
    }

    private JTabbedPane createMainTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.putClientProperty("JTabbedPane.tabHeight", 36);
        tabs.putClientProperty("JTabbedPane.tabInsets", new Insets(8, 16, 8, 16));
        tabs.setFont(tabs.getFont().deriveFont(Font.PLAIN, 14f));
        tabs.addTab("ダッシュボード", createDashboard());
        tabs.addTab("コンソール", console);
        tabs.addTab("リスト管理", playlistManager);
        return tabs;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(createSectionBorder(12));
        header.setBackground(Color.WHITE);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("JMusicBot JP Control Center");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        JLabel subtitle = new JLabel("運用状況を確認しながら、ログを検索・制御できます");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 16f));

        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(subtitle);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        statusBadge.setOpaque(true);
        statusBadge.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        JButton clearButton = new JButton("ログをクリア");
        JButton copyButton = new JButton("ログをコピー");
        styleHeaderButton(clearButton);
        styleHeaderButton(copyButton);
        clearButton.addActionListener(e -> console.clearConsole());
        copyButton.addActionListener(e -> console.copyAllLogs());

        right.add(statusBadge);
        right.add(clearButton);
        right.add(copyButton);

        header.add(textPanel, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel createDashboard() {
        JPanel dashboard = new JPanel(new BorderLayout(12, 12));
        dashboard.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        dashboard.setOpaque(false);

        JPanel cards = new JPanel(new GridLayout(2, 4, 12, 12));
        cards.setOpaque(false);
        cards.add(createStatCard("Bot状態", botStatusValue));
        cards.add(createStatCard("稼働時間", uptimeValue));
        cards.add(createStatCard("接続サーバー数", guildCountValue));
        cards.add(createStatCard("Gateway Ping", pingValue));
        cards.add(createStatCard("メモリ使用量", memoryValue));
        cards.add(createStatCard("コンソール行数", logLineValue));
        cards.add(createStatCard("ffmpeg", ffmpegValue));
        cards.add(createStatCard("yt-dlp バージョン", ytDlpVersionValue));

        JPanel tips = new JPanel();
        tips.setOpaque(true);
        tips.setBackground(Color.WHITE);
        tips.setLayout(new BoxLayout(tips, BoxLayout.Y_AXIS));
        tips.setBorder(createSectionBorder(10));
        tips.add(new JLabel("・コンソールタブで Ctrl+F によりログ検索できます"));
        tips.add(Box.createVerticalStrut(6));
        tips.add(new JLabel("・大量ログ時は一時停止で表示を止めて確認してください"));
        tips.add(Box.createVerticalStrut(6));
        tips.add(new JLabel("・自動スクロールを無効化すると過去ログを追跡しやすくなります"));

        dashboard.add(cards, BorderLayout.NORTH);
        dashboard.add(tips, BorderLayout.CENTER);
        return dashboard;
    }

    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(4, 8));
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setBorder(createSectionBorder(10));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        titleLabel.setForeground(new Color(105, 111, 118));
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 22f));
        valueLabel.setForeground(new Color(25, 34, 48));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void refreshStatus() {
        JDA jda = bot.getJDA();
        boolean connected = jda != null && jda.getStatus() == JDA.Status.CONNECTED;

        if (jda == null) {
            botStatusValue.setText("起動中");
            guildCountValue.setText("0");
            pingValue.setText("-");
            setStatusBadge("起動中", new Color(255, 242, 204), new Color(130, 99, 40));
            listTargetsLoadedAfterConnect = false;
        } else {
            botStatusValue.setText(jda.getStatus().name());
            guildCountValue.setText(String.valueOf(jda.getGuilds().size()));
            pingValue.setText(jda.getGatewayPing() + " ms");
            if (connected) {
                setStatusBadge("接続中", new Color(218, 242, 220), new Color(36, 107, 52));
                if (!listTargetsLoadedAfterConnect) {
                    playlistManager.onBotConnected();
                    listTargetsLoadedAfterConnect = true;
                }
            } else {
                setStatusBadge("接続待機", new Color(255, 242, 204), new Color(130, 99, 40));
                listTargetsLoadedAfterConnect = false;
            }
        }

        Duration uptime = Duration.between(startedAt, Instant.now());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        uptimeValue.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        memoryValue.setText(usedMb + " / " + maxMb + " MB");

        logLineValue.setText(String.valueOf(console.getLogLineCount()));
        refreshExternalToolStatusIfNeeded();
    }

    private void refreshExternalToolStatusIfNeeded() {
        Instant now = Instant.now();
        if (Duration.between(lastExternalToolsRefreshedAt, now).compareTo(Duration.ofSeconds(30)) < 0) {
            return;
        }
        lastExternalToolsRefreshedAt = now;

        boolean ffmpegInstalled = bot.getPlayerManager().isFfmpegAvailable();
        ffmpegValue.setText(ffmpegInstalled ? "インストール済み" : "未検出");

        String ytDlpVersion = bot.getPlayerManager().getYtDlpVersion();
        ytDlpVersionValue.setText(ytDlpVersion == null ? "未検出" : ytDlpVersion);
    }

    private void setStatusBadge(String text, Color background, Color foreground) {
        statusBadge.setText(text);
        statusBadge.setBackground(background);
        statusBadge.setForeground(foreground);
    }

    private void styleHeaderButton(JButton button) {
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 14f));
        button.setMargin(new Insets(8, 14, 8, 14));
    }

    private Border createSectionBorder(int padding) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                BorderFactory.createEmptyBorder(padding, padding, padding, padding)
        );
    }
}
