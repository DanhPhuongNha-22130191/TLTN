package secretchat.chat.view;

import com.pavlobu.emojitextflow.EmojiTextFlow;
import com.pavlobu.emojitextflow.EmojiTextFlowParameters;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import secretchat.util.LinkUtils;

import java.util.List;

public final class LinkTextView {
    private LinkTextView() {
    }

    public static TextFlow create(String content, String textStyleClass) {
        TextFlow flow = new TextFlow();
        update(flow, content, textStyleClass);
        return flow;
    }

    public static void update(TextFlow flow, String content, String textStyleClass) {
        String value = content == null ? "" : content;
        flow.getChildren().clear();
        int cursor = 0;
        for (LinkUtils.LinkMatch link : LinkUtils.findLinks(value)) {
            addText(flow, value.substring(cursor, link.start()), textStyleClass);
            Hyperlink hyperlink = new Hyperlink(link.value());
            hyperlink.getStyleClass().add("message-hyperlink");
            hyperlink.setOnAction(event -> open(link.value()));
            flow.getChildren().add(hyperlink);
            cursor = link.end();
        }
        addText(flow, value.substring(cursor), textStyleClass);
    }

    private static void addText(TextFlow flow, String value, String styleClass) {
        if (value.isEmpty()) return;
        EmojiTextFlow parsed = new EmojiTextFlow(parameters(styleClass));
        parsed.parseAndAppend(value);
        List<Node> nodes = List.copyOf(parsed.getChildren());
        parsed.getChildren().clear();
        nodes.forEach(node -> node.getStyleClass().add(styleClass));
        flow.getChildren().addAll(nodes);
    }

    private static EmojiTextFlowParameters parameters(String styleClass) {
        EmojiTextFlowParameters parameters = new EmojiTextFlowParameters();
        parameters.setEmojiScaleFactor(1);
        parameters.setTextAlignment(TextAlignment.LEFT);
        parameters.setFont(Font.font("System", 14));
        parameters.setTextColor("my-message-text".equals(styleClass)
                ? Color.WHITE : Color.web("#1e1b4b"));
        return parameters;
    }

    private static void open(String url) {
        try {
            LinkUtils.open(url);
        } catch (Exception ignored) {
            // Conversation-level views display errors for explicit open actions.
        }
    }
}
