package secretchat.util;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinkUtils {
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?i)(?<![@\\w])((?:https?://|www\\.)[^\\s<>]+"
                    + "|(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:/[^\\s<>]*)?)");
    private static final String TRAILING_PUNCTUATION = ".,;:!?)]}";

    private LinkUtils() {
    }

    public static List<LinkMatch> findLinks(String text) {
        List<LinkMatch> links = new ArrayList<>();
        if (text == null || text.isBlank()) return links;
        Matcher matcher = LINK_PATTERN.matcher(text);
        while (matcher.find()) {
            String value = trimTrailingPunctuation(matcher.group(1));
            if (!value.isBlank()) {
                links.add(new LinkMatch(matcher.start(1), matcher.start(1) + value.length(), value));
            }
        }
        return links;
    }

    public static String normalize(String url) {
        String value = trimTrailingPunctuation(url == null ? "" : url.trim());
        if (value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8)) {
            return value;
        }
        return "https://" + value;
    }

    public static void open(String url) throws Exception {
        String normalized = normalize(url);
        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(normalized));
            return;
        }
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            Runtime.getRuntime().exec(
                    new String[]{"rundll32", "url.dll,FileProtocolHandler", normalized});
        } else if (os.contains("mac")) {
            Runtime.getRuntime().exec(new String[]{"open", normalized});
        } else if (os.contains("nix") || os.contains("nux")) {
            Runtime.getRuntime().exec(new String[]{"xdg-open", normalized});
        } else {
            throw new IllegalStateException("Hệ điều hành không hỗ trợ mở liên kết.");
        }
    }

    private static String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    public record LinkMatch(int start, int end, String value) {
    }
}
