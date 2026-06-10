package secretchat.chat.view;

import com.pavlobu.emojitextflow.EmojiTextFlow;
import com.pavlobu.emojitextflow.EmojiTextFlowParameters;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.function.Consumer;

public final class EmojiPicker {
    private static final List<String> REACTION_EMOJIS =
            List.of("👍", "❤️", "😂", "😮", "😢", "😡");
    private static final List<String> MESSAGE_EMOJIS = List.of(
            "😀", "😃", "😄", "😁", "😂", "😊",
            "😍", "😘", "😎", "🤔", "😢", "😭",
            "😡", "👍", "👏", "🙏", "❤️", "🎉");

    private EmojiPicker() {
    }

    public static void showMessagePicker(Node owner, Side side, Consumer<String> selectionHandler) {
        show(owner, side, MESSAGE_EMOJIS, selectionHandler);
    }

    public static void showReactionPicker(Node owner, Side side, Consumer<String> selectionHandler) {
        show(owner, side, REACTION_EMOJIS, selectionHandler);
    }

    private static void show(
            Node owner,
            Side side,
            List<String> emojis,
            Consumer<String> selectionHandler) {
        ContextMenu menu = new ContextMenu();
        FlowPane choices = new FlowPane(4, 4);
        choices.setPrefWrapLength(250);
        choices.getStyleClass().add("emoji-picker");
        for (String emoji : emojis) {
            Button button = new Button();
            button.getStyleClass().add("emoji-picker-button");
            button.setGraphic(emojiGraphic(emoji, 22));
            button.setOnAction(event -> {
                selectionHandler.accept(emoji);
                menu.hide();
            });
            choices.getChildren().add(button);
        }
        CustomMenuItem item = new CustomMenuItem(choices, false);
        item.getStyleClass().add("emoji-picker-menu-item");
        menu.getItems().add(item);
        menu.show(owner, side, 0, 0);
    }

    public static EmojiTextFlow emojiGraphic(String emoji, double size) {
        EmojiTextFlowParameters parameters = new EmojiTextFlowParameters();
        parameters.setEmojiScaleFactor(1);
        parameters.setTextAlignment(TextAlignment.CENTER);
        parameters.setFont(Font.font("System", size));
        parameters.setTextColor(Color.BLACK);
        EmojiTextFlow flow = new EmojiTextFlow(parameters);
        flow.parseAndAppend(emoji);
        return flow;
    }
}
