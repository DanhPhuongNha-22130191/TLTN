// Copyright (c) 2020, Pavlo Buidenkov. All rights reserved.
// Licensed under the BSD 3-Clause License.
package com.pavlobu.emojitextflow;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class EmojiTextFlowParameters {
    private TextAlignment textAlignment;
    private Font font = Font.getDefault();
    private Color textColor;
    private double emojiScaleFactor = 1;

    public TextAlignment getTextAlignment() {
        return textAlignment;
    }

    public void setTextAlignment(TextAlignment textAlignment) {
        this.textAlignment = textAlignment;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        this.font = font;
    }

    public Color getTextColor() {
        return textColor;
    }

    public void setTextColor(Color textColor) {
        this.textColor = textColor;
    }

    public double getEmojiFitWidth() {
        return font.getSize() * emojiScaleFactor;
    }

    public double getEmojiFitHeight() {
        return font.getSize() * emojiScaleFactor;
    }

    public void setEmojiScaleFactor(double emojiScaleFactor) {
        this.emojiScaleFactor = emojiScaleFactor;
    }
}
