package com.pavlobu.emojitextflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmojiParserTest {

    @Test
    void parsesUnicodeEmojiAlongsideText() {
        Queue<Object> values = EmojiParser.getInstance().toEmojiAndText("Xin chào 😀");

        assertEquals("Xin chào ", values.remove());
        assertEquals("😀", assertInstanceOf(Emoji.class, values.remove()).getUnicode());
    }

    @Test
    void convertsShortnameBeforeParsing() {
        Queue<Object> values = EmojiParser.getInstance().toEmojiAndText(":smile:");

        assertInstanceOf(Emoji.class, values.remove());
    }

    @Test
    void parsesEmojiVariationSequenceAsOneEmoji() {
        Queue<Object> values = EmojiParser.getInstance().toEmojiAndText("❤️");

        assertEquals("❤️", assertInstanceOf(Emoji.class, values.remove()).getUnicode());
        assertTrue(values.isEmpty());
    }

    @Test
    void exposesCompleteEmojiCatalogForPicker() {
        List<Emoji> emojis = EmojiParser.getInstance().getAvailableEmojis();

        assertTrue(emojis.size() > 24);
        assertFalse(emojis.stream().map(Emoji::getUnicode).toList().contains(""));
        assertTrue(emojis.stream().map(Emoji::getUnicode).toList().contains("❤️"));
    }
}
