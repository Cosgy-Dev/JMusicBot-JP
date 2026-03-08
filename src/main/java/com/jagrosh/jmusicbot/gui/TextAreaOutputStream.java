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

import javax.swing.*;
import java.awt.*;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;

/**
 * @author Lawrence Dol
 */
public class TextAreaOutputStream extends OutputStream {
    public interface BacklogListener {
        void onBacklogChanged(int pendingMessages);
    }

    private final byte[] oneByte;
    private Appender appender;

    public TextAreaOutputStream(JTextArea txtara) {
        this(txtara, 1000);
    }

    public TextAreaOutputStream(JTextArea txtara, int maxlin) {
        if (maxlin < 1) {
            throw new IllegalArgumentException("TextAreaOutputStreamの最大行数は正数でなければなりません(value=" + maxlin + ")");
        }
        oneByte = new byte[1];
        appender = new Appender(txtara, maxlin);
    }

    private static String bytesToString(byte[] ba, int str, int len) {
        try {
            return new String(ba, str, len, System.getProperty("file.encoding"));
        } catch (UnsupportedEncodingException thr) {
            return new String(ba, str, len);
        }
    }

    /**
     * 現在のコンソールテキスト領域をクリアします。
     */
    public synchronized void clear() {
        if (appender != null) {
            appender.clear();
        }
    }

    public synchronized void setPaused(boolean paused) {
        if (appender != null) {
            appender.setPaused(paused);
        }
    }

    public synchronized boolean isPaused() {
        return appender != null && appender.isPaused();
    }

    public synchronized void setBacklogListener(BacklogListener listener) {
        if (appender != null) {
            appender.setBacklogListener(listener);
        }
    }

    @Override
    public synchronized void close() {
        appender = null;
    }

    @Override
    public synchronized void flush() {
        /* empty */
    }

    @Override
    public synchronized void write(int val) {
        oneByte[0] = (byte) val;
        write(oneByte, 0, 1);
    }

    @Override
    public synchronized void write(byte[] ba) {
        write(ba, 0, ba.length);
    }

    @Override
    public synchronized void write(byte[] ba, int str, int len) {
        if (appender != null) {
            appender.append(bytesToString(ba, str, len));
        }
    }

    static class Appender implements Runnable {
        private static final String EOL1 = "\n";
        private static final String EOL2 = System.getProperty("line.separator", EOL1);

        private final JTextArea textArea;
        private final int maxLines;
        private final LinkedList<Integer> lengths;
        private final LinkedList<String> values;
        private final LinkedList<String> pausedValues;
        private BacklogListener backlogListener;

        private int curLength;
        private boolean clear;
        private boolean queue;
        private boolean paused;

        Appender(JTextArea txtara, int maxlin) {
            textArea = txtara;
            maxLines = maxlin;
            lengths = new LinkedList<>();
            values = new LinkedList<>();
            pausedValues = new LinkedList<>();
            curLength = 0;
            clear = false;
            queue = true;
            paused = false;
        }

        private synchronized void append(String val) {
            if (paused) {
                pausedValues.add(val);
                notifyBacklogChanged();
                return;
            }
            values.add(val);
            notifyBacklogChanged();
            if (queue) {
                queue = false;
                EventQueue.invokeLater(this);
            }
        }

        private synchronized void clear() {
            clear = true;
            curLength = 0;
            lengths.clear();
            values.clear();
            pausedValues.clear();
            notifyBacklogChanged();
            if (queue) {
                queue = false;
                EventQueue.invokeLater(this);
            }
        }

        private synchronized void setPaused(boolean pause) {
            if (this.paused == pause) {
                return;
            }
            this.paused = pause;
            if (!pause && !pausedValues.isEmpty()) {
                values.addAll(pausedValues);
                pausedValues.clear();
                notifyBacklogChanged();
                if (queue) {
                    queue = false;
                    EventQueue.invokeLater(this);
                }
            }
        }

        private synchronized boolean isPaused() {
            return paused;
        }

        private synchronized void setBacklogListener(BacklogListener listener) {
            this.backlogListener = listener;
            notifyBacklogChanged();
        }

        private void notifyBacklogChanged() {
            BacklogListener listener = backlogListener;
            if (listener == null) {
                return;
            }
            int backlogSize = values.size() + pausedValues.size();
            if (SwingUtilities.isEventDispatchThread()) {
                listener.onBacklogChanged(backlogSize);
            } else {
                SwingUtilities.invokeLater(() -> listener.onBacklogChanged(backlogSize));
            }
        }

        @Override
        public synchronized void run() {
            final int maxBatchMessages = 200;
            final int maxBatchChars = 64 * 1024;
            if (clear) {
                textArea.setText("");
            }
            int processedMessages = 0;
            int processedChars = 0;
            while (!values.isEmpty() && processedMessages < maxBatchMessages && processedChars < maxBatchChars) {
                String val = values.removeFirst();
                curLength += val.length();
                if (val.endsWith(EOL1) || val.endsWith(EOL2)) {
                    if (lengths.size() >= maxLines) {
                        textArea.replaceRange("", 0, lengths.removeFirst());
                    }
                    lengths.addLast(curLength);
                    curLength = 0;
                }
                textArea.append(val);
                processedMessages++;
                processedChars += val.length();
            }
            clear = false;
            notifyBacklogChanged();
            if (values.isEmpty()) {
                queue = true;
            } else {
                EventQueue.invokeLater(this);
            }
        }
    }
}
