package secretchat.chat.viewmodel;

public final class ChatViewModelFactory {

    private ChatViewModelFactory() {
    }

    public static ChatViewModel create() {
        return new ChatViewModel();
    }
}
