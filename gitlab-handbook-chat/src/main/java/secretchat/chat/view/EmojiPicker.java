package secretchat.chat.view;

import com.pavlobu.emojitextflow.Emoji;
import com.pavlobu.emojitextflow.EmojiParser;
import com.pavlobu.emojitextflow.EmojiTextFlow;
import com.pavlobu.emojitextflow.EmojiTextFlowParameters;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class EmojiPicker {
    private static final int EMOJIS_PER_PAGE = 24;
    private static final List<String> REACTION_EMOJIS =
            List.of("👍", "❤️", "😂", "😮", "😢", "😡");
    private static final List<String> MESSAGE_EMOJIS = EmojiParser.getInstance()
            .getAvailableEmojis().stream()
            .map(Emoji::getUnicode)
            .toList();

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
        AtomicInteger currentPage = new AtomicInteger();
        int pageCount = Math.max(1, (emojis.size() + EMOJIS_PER_PAGE - 1) / EMOJIS_PER_PAGE);
        Button previous = new Button("‹");
        Button next = new Button("›");
        Label pageLabel = new Label();
        previous.getStyleClass().add("emoji-picker-page-button");
        next.getStyleClass().add("emoji-picker-page-button");
        pageLabel.getStyleClass().add("emoji-picker-page-label");

        Runnable renderPage = () -> {
            choices.getChildren().clear();
            int page = currentPage.get();
            int start = page * EMOJIS_PER_PAGE;
            int end = Math.min(start + EMOJIS_PER_PAGE, emojis.size());
            for (String emoji : emojis.subList(start, end)) {
                Button button = new Button();
                button.getStyleClass().add("emoji-picker-button");
                button.setGraphic(emojiGraphic(emoji, 22));
                button.setOnAction(event -> {
                    selectionHandler.accept(emoji);
                    menu.hide();
                });
                choices.getChildren().add(button);
            }
            pageLabel.setText((page + 1) + " / " + pageCount);
            previous.setDisable(page == 0);
            next.setDisable(page >= pageCount - 1);
        };
        previous.setOnAction(event -> {
            currentPage.decrementAndGet();
            renderPage.run();
        });
        next.setOnAction(event -> {
            currentPage.incrementAndGet();
            renderPage.run();
        });

        HBox pagination = new HBox(10, previous, pageLabel, next);
        pagination.setAlignment(Pos.CENTER);
        pagination.getStyleClass().add("emoji-picker-pagination");
        pagination.setManaged(pageCount > 1);
        pagination.setVisible(pageCount > 1);
        VBox content = new VBox(4, choices, pagination);
        renderPage.run();

        CustomMenuItem item = new CustomMenuItem(content, false);
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
