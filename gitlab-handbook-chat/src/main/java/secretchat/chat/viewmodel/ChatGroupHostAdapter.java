package secretchat.chat.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import secretchat.dto.response.ConversationResponse;
import secretchat.dto.response.GroupResponse;
import secretchat.dto.response.UserResponse;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Bridges group operations to JavaFX state without bloating the public view model.
 */
final class ChatGroupHostAdapter implements ChatGroupCoordinator.Host {
    private final Supplier<String> tokenSupplier;
    private final Supplier<String> currentUserIdSupplier;
    private final Map<String, UserResponse> usersByName;
    private final Map<String, GroupResponse> groupsByName;
    private final Map<String, ConversationResponse> groupConversations;
    private final ObservableList<String> groupChats;
    private final ObservableList<ChatViewModel.MessageItem> messages;
    private final ObservableList<String> members;
    private final ObservableList<String> sentFiles;
    private final ObservableList<String> sentLinks;
    private final ObservableList<ChatViewModel.PinnedMessageItem> pinnedMessages;
    private final StringProperty currentChatName;
    private final BooleanProperty currentChatIsGroup;
    private final ObjectProperty<ConversationResponse> activeConversation;
    private final Function<String, String> displayNameResolver;
    private final Consumer<GroupResponse> groupInfoLoader;
    private final Consumer<String> errorConsumer;
    private final Consumer<String> notificationConsumer;

    ChatGroupHostAdapter(
            Supplier<String> tokenSupplier,
            Supplier<String> currentUserIdSupplier,
            Map<String, UserResponse> usersByName,
            Map<String, GroupResponse> groupsByName,
            Map<String, ConversationResponse> groupConversations,
            ObservableList<String> groupChats,
            ObservableList<ChatViewModel.MessageItem> messages,
            ObservableList<String> members,
            ObservableList<String> sentFiles,
            ObservableList<String> sentLinks,
            ObservableList<ChatViewModel.PinnedMessageItem> pinnedMessages,
            StringProperty currentChatName,
            BooleanProperty currentChatIsGroup,
            ObjectProperty<ConversationResponse> activeConversation,
            Function<String, String> displayNameResolver,
            Consumer<GroupResponse> groupInfoLoader,
            Consumer<String> errorConsumer,
            Consumer<String> notificationConsumer) {
        this.tokenSupplier = tokenSupplier;
        this.currentUserIdSupplier = currentUserIdSupplier;
        this.usersByName = usersByName;
        this.groupsByName = groupsByName;
        this.groupConversations = groupConversations;
        this.groupChats = groupChats;
        this.messages = messages;
        this.members = members;
        this.sentFiles = sentFiles;
        this.sentLinks = sentLinks;
        this.pinnedMessages = pinnedMessages;
        this.currentChatName = currentChatName;
        this.currentChatIsGroup = currentChatIsGroup;
        this.activeConversation = activeConversation;
        this.displayNameResolver = displayNameResolver;
        this.groupInfoLoader = groupInfoLoader;
        this.errorConsumer = errorConsumer;
        this.notificationConsumer = notificationConsumer;
    }

    @Override public String token() { return tokenSupplier.get(); }
    @Override public String currentUserId() { return currentUserIdSupplier.get(); }
    @Override public String currentChatName() { return currentChatName.get(); }
    @Override public ConversationResponse activeConversation() {
        return activeConversation.get();
    }
    @Override public GroupResponse groupByName(String name) {
        return groupsByName.get(name);
    }
    @Override public UserResponse userByName(String name) {
        return usersByName.get(name);
    }
    @Override public Set<Map.Entry<String, UserResponse>> users() {
        return usersByName.entrySet();
    }
    @Override public String userDisplayName(String userId) {
        return displayNameResolver.apply(userId);
    }
    @Override public boolean isGroupCreator(String groupName) {
        GroupResponse group = groupsByName.get(groupName);
        return group != null && currentUserIdSupplier.get().equals(group.getCreatorId());
    }

    @Override
    public void groupCreated(GroupResponse group, ConversationResponse conversation) {
        groupsByName.put(group.getName(), group);
        groupChats.add(group.getName());
        groupConversations.put(group.getId(), conversation);
    }

    @Override
    public void groupUpdated(GroupResponse group) {
        groupsByName.put(currentChatName.get(), group);
    }

    @Override
    public void groupLeft(String groupName) {
        groupChats.remove(groupName);
        groupsByName.remove(groupName);
        clearSelection();
    }

    @Override
    public void groupDeleted(GroupResponse group) {
        groupChats.remove(group.getName());
        groupsByName.remove(group.getName());
        groupConversations.remove(group.getId());
        pinnedMessages.clear();
        clearSelection();
    }

    @Override public void addMemberName(String name) { members.add(name); }
    @Override public void removeMemberName(String name) { members.remove(name); }
    @Override public void reloadGroupInfo(GroupResponse group) {
        groupInfoLoader.accept(group);
    }
    @Override public void showError(String message) { errorConsumer.accept(message); }
    @Override public void showNotification(String message) {
        notificationConsumer.accept(message);
    }

    private void clearSelection() {
        messages.clear();
        members.clear();
        sentFiles.clear();
        sentLinks.clear();
        currentChatName.set(null);
        currentChatIsGroup.set(false);
        activeConversation.set(null);
    }
}
