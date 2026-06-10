package com.pavlobu.emojitextflow;

import org.junit.jupiter.api.Test;

import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
}
