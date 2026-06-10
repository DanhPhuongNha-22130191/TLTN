package secretchat.chat.view;

import javafx.scene.control.Hyperlink;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import secretchat.util.LinkUtils;

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
        Text text = new Text(value);
        text.getStyleClass().add(styleClass);
        flow.getChildren().add(text);
    }

    private static void open(String url) {
        try {
            LinkUtils.open(url);
        } catch (Exception ignored) {
            // Conversation-level views display errors for explicit open actions.
        }
    }
}
