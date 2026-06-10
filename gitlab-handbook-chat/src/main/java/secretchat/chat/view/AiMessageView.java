package secretchat.chat.view;

import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

public final class AiMessageView {
    private static final String[] DISCLAIMER_MARKERS = {
            "\n\nLưu ý:",
            "\nLưu ý:",
            "\n\n*(Câu trả lời này",
            "\n*(Câu trả lời này",
            "\n\n(Câu trả lời này",
            "\n(Câu trả lời này"
    };

    private AiMessageView() {
    }

    public static VBox create(String content) {
        VBox bubble = new VBox();
        bubble.setMaxWidth(420);
        bubble.getStyleClass().addAll("chat-message-bubble", "ai-message");
        update(bubble, content);
        return bubble;
    }

    public static void update(VBox bubble, String content) {
        ParsedAnswer parsed = parse(content);
        Label answer = wrappedLabel(parsed.answer(), "ai-answer-text");
        bubble.getChildren().setAll(answer);

        if (parsed.disclaimer() != null && !parsed.disclaimer().isBlank()) {
            Separator separator = new Separator();
            separator.getStyleClass().add("ai-disclaimer-separator");
            bubble.getChildren().addAll(
                    separator, wrappedLabel(parsed.disclaimer(), "ai-disclaimer-text"));
        }
    }

    private static Label wrappedLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(390);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static ParsedAnswer parse(String content) {
        String answer = content == null ? "" : content.trim();
        int disclaimerStart = findDisclaimerStart(answer);
        if (disclaimerStart < 0) return new ParsedAnswer(answer, null);

        String disclaimer = stripMarkdownItalics(answer.substring(disclaimerStart).trim());
        return new ParsedAnswer(answer.substring(0, disclaimerStart).trim(), disclaimer);
    }

    private static int findDisclaimerStart(String text) {
        for (String marker : DISCLAIMER_MARKERS) {
            int index = text.indexOf(marker);
            if (index >= 0) return index;
        }
        return -1;
    }

    private static String stripMarkdownItalics(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("*")) cleaned = cleaned.substring(1).trim();
        if (cleaned.endsWith("*")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        return cleaned;
    }

    private record ParsedAnswer(String answer, String disclaimer) {
    }
}
