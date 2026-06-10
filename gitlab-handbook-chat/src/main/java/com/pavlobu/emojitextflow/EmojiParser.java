// Copyright (c) 2020, Pavlo Buidenkov. All rights reserved.
// Licensed under the BSD 3-Clause License.
package com.pavlobu.emojitextflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmojiParser {
    private static final EmojiParser INSTANCE = new EmojiParser();

    private final Map<String, String> unicodeToHex = new HashMap<>();
    private final Map<String, String> unicodeToShortname = new HashMap<>();
    private final Map<String, String> shortnameToUnicode = new HashMap<>();
    private final List<Emoji> availableEmojis = new ArrayList<>();
    private final Pattern unicodePattern;
    private final Pattern shortnamePattern;

    private EmojiParser() {
        loadEmojiData();
        unicodePattern = Pattern.compile(unicodeToHex.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right)
                .orElse("(?!x)x"));
        shortnamePattern = Pattern.compile(shortnameToUnicode.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right)
                .orElse("(?!x)x"));
    }

    public static EmojiParser getInstance() {
        return INSTANCE;
    }

    public Queue<Object> toEmojiAndText(String value) {
        Queue<Object> result = new ArrayDeque<>();
        String text = replaceShortnames(value == null ? "" : value);
        Matcher matcher = unicodePattern.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                result.add(text.substring(cursor, matcher.start()));
            }
            String unicode = matcher.group();
            result.add(new Emoji(
                    unicodeToShortname.get(unicode),
                    unicode,
                    unicodeToHex.get(unicode)));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            result.add(text.substring(cursor));
        }
        return result;
    }

    public List<Emoji> getAvailableEmojis() {
        return List.copyOf(availableEmojis);
    }

    private void loadEmojiData() {
        try (InputStream input = EmojiParser.class.getClassLoader()
                .getResourceAsStream("emoji.json")) {
            if (input == null) {
                throw new IllegalStateException("emoji.json was not found");
            }
            JsonNode root = new ObjectMapper().readTree(input);
            List<OrderedEmoji> orderedEmojis = new ArrayList<>();
            for (Map.Entry<String, JsonNode> emojiData : root.properties()) {
                JsonNode entry = emojiData.getValue();
                String shortname = entry.path("shortname").asText();
                String primaryHex = entry.path("unicode").asText();
                if (primaryHex.isBlank()) continue;
                register(primaryHex, primaryHex, shortname);
                List<String> alternatives = new ArrayList<>();
                JsonNode unicodeAlt = entry.path("unicode_alt");
                if (unicodeAlt.isTextual() && !unicodeAlt.asText().isBlank()) {
                    alternatives.add(unicodeAlt.asText());
                } else if (unicodeAlt.isArray()) {
                    unicodeAlt.forEach(node -> alternatives.add(node.asText()));
                }
                alternatives.forEach(hex -> register(hex, primaryHex, shortname));
                if (!shortname.isBlank()) {
                    shortnameToUnicode.put(shortname, toUnicode(primaryHex));
                }
                entry.path("aliases").forEach(alias -> {
                    String value = alias.asText();
                    if (value.startsWith(":") && value.endsWith(":")) {
                        shortnameToUnicode.put(value, toUnicode(primaryHex));
                    }
                });
                int order = entry.path("emoji_order").asInt(Integer.MAX_VALUE);
                String pickerHex = alternatives.isEmpty() ? primaryHex : alternatives.getFirst();
                orderedEmojis.add(new OrderedEmoji(
                        order, new Emoji(shortname, toUnicode(pickerHex), primaryHex.toLowerCase())));
            }
            Map<String, Emoji> uniqueEmojis = new LinkedHashMap<>();
            orderedEmojis.stream()
                    .sorted(Comparator.comparingInt(OrderedEmoji::order))
                    .map(OrderedEmoji::emoji)
                    .forEach(emoji -> uniqueEmojis.putIfAbsent(emoji.getUnicode(), emoji));
            availableEmojis.addAll(uniqueEmojis.values());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load emoji metadata", error);
        }
    }

    private void register(String unicodeHex, String imageHex, String shortname) {
        if (unicodeHex == null || unicodeHex.isBlank()) return;
        String unicode = toUnicode(unicodeHex);
        unicodeToHex.put(unicode, imageHex.toLowerCase());
        unicodeToShortname.put(unicode, shortname);
    }

    private String replaceShortnames(String value) {
        Matcher matcher = shortnamePattern.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    shortnameToUnicode.get(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String toUnicode(String hex) {
        StringBuilder value = new StringBuilder();
        for (String part : hex.split("-")) {
            value.appendCodePoint(Integer.parseInt(part, 16));
        }
        return value.toString();
    }

    private record OrderedEmoji(int order, Emoji emoji) {
    }
}
