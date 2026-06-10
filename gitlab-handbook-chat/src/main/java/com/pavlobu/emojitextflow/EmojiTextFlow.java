// Copyright (c) 2020, Pavlo Buidenkov. All rights reserved.
// Licensed under the BSD 3-Clause License.
package com.pavlobu.emojitextflow;

import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

import java.net.URL;
import java.util.Queue;

public class EmojiTextFlow extends TextFlow {
    private static final System.Logger LOGGER =
            System.getLogger(EmojiTextFlow.class.getName());

    private final EmojiTextFlowParameters parameters;

    public EmojiTextFlow() {
        parameters = new EmojiTextFlowParameters();
        parameters.setEmojiScaleFactor(1);
        parameters.setTextAlignment(TextAlignment.CENTER);
        parameters.setFont(Font.font("System", FontWeight.NORMAL, 35));
        parameters.setTextColor(Color.BLACK);
    }

    public EmojiTextFlow(EmojiTextFlowParameters parameters) {
        this.parameters = parameters;
        if (parameters.getTextAlignment() != null) {
            setTextAlignment(parameters.getTextAlignment());
        }
    }

    public void parseAndAppend(String message) {
        Queue<Object> values = EmojiParser.getInstance().toEmojiAndText(message);
        while (!values.isEmpty()) {
            Object value = values.poll();
            if (value instanceof String text) {
                addText(text);
            } else if (value instanceof Emoji emoji) {
                addEmoji(emoji);
            }
        }
    }

    private void addText(String value) {
        Text text = new Text(value);
        text.setFont(parameters.getFont());
        if (parameters.getTextColor() != null) {
            text.setFill(parameters.getTextColor());
        }
        getChildren().add(text);
    }

    private void addEmoji(Emoji emoji) {
        URL resource = getClass().getClassLoader()
                .getResource("emoji_images/" + emoji.getHex() + ".png");
        if (resource == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Emoji image not found: " + emoji.getHex());
            addText(emoji.getUnicode());
            return;
        }
        ImageView image = new ImageView(
                EmojiImageCache.getInstance().getImage(resource.toExternalForm()));
        image.setFitWidth(parameters.getEmojiFitWidth());
        image.setFitHeight(parameters.getEmojiFitHeight());
        getChildren().add(image);
    }
}
