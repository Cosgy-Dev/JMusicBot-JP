package com.jagrosh.jmusicbot.gui;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;

class ThemeAwareLineBorder extends AbstractBorder {
    private final String colorKey;
    private final Color fallback;

    ThemeAwareLineBorder() {
        this("Component.borderColor", new Color(220, 224, 230));
    }

    ThemeAwareLineBorder(String colorKey, Color fallback) {
        this.colorKey = colorKey;
        this.fallback = fallback;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color color = UIManager.getColor(colorKey);
        g.setColor(color != null ? color : fallback);
        g.drawRect(x, y, width - 1, height - 1);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(1, 1, 1, 1);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(1, 1, 1, 1);
        return insets;
    }
}
