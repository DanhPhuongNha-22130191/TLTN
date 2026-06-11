package secretchat.chat.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import secretchat.dto.response.MessageReactionResponse;
import secretchat.dto.response.MessageResponse;

import java.util.List;

/**
 * Observable presentation state for a chat message.
 */
public class ChatMessageItem {
    private final MessageResponse response;
    private final String senderName;
    private final StringProperty content = new SimpleStringProperty();
    private final String time;
    private final boolean isMe;
    private final boolean isFile;
    private final BooleanProperty isDeleted = new SimpleBooleanProperty();
    private final BooleanProperty isDeletedForMe = new SimpleBooleanProperty();
    private final StringProperty status = new SimpleStringProperty("SENT");
    private final BooleanProperty starred = new SimpleBooleanProperty();
    private final BooleanProperty pinned = new SimpleBooleanProperty();
    private final DoubleProperty uploadProgress = new SimpleDoubleProperty(-1);
    private final ObservableMap<String, String> reactions = FXCollections.observableHashMap();

    public ChatMessageItem(
            MessageResponse response,
            String senderName,
            String content,
            String time,
            boolean isMe,
            boolean isFile,
            boolean isDeleted,
            boolean isDeletedForMe) {
        this.response = response;
        this.senderName = senderName;
        this.content.set(content);
        this.time = time;
        this.isMe = isMe;
        this.isFile = isFile;
        this.isDeleted.set(isDeleted);
        this.isDeletedForMe.set(isDeletedForMe);
        if (response != null) {
            status.set(response.getStatus() == null ? "SENT" : response.getStatus());
            starred.set(response.isStarred());
            pinned.set(response.isPinned());
            setReactions(response.getReactions());
        }
    }

    public MessageResponse getResponse() { return response; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content.get(); }
    public StringProperty contentProperty() { return content; }
    public String getTime() { return time; }
    public boolean isMe() { return isMe; }
    public boolean isFile() { return isFile; }

    public boolean isDeleted() { return isDeleted.get(); }
    public void setDeleted(boolean value) { isDeleted.set(value); }
    public BooleanProperty isDeletedProperty() { return isDeleted; }

    public boolean isDeletedForMe() { return isDeletedForMe.get(); }
    public void setDeletedForMe(boolean value) { isDeletedForMe.set(value); }
    public BooleanProperty isDeletedForMeProperty() { return isDeletedForMe; }
    public String getStatus() { return status.get(); }
    public void setStatus(String value) { status.set(value); }
    public StringProperty statusProperty() { return status; }
    public boolean isStarred() { return starred.get(); }
    public BooleanProperty starredProperty() { return starred; }
    public boolean isPinned() { return pinned.get(); }

    public void setPinned(boolean value) {
        pinned.set(value);
        if (response != null) response.setPinned(value);
    }

    public BooleanProperty pinnedProperty() { return pinned; }
    public double getUploadProgress() { return uploadProgress.get(); }
    public void setUploadProgress(double value) { uploadProgress.set(value); }
    public DoubleProperty uploadProgressProperty() { return uploadProgress; }
    public ObservableMap<String, String> getReactions() { return reactions; }
    public String getReactionFor(String userId) { return reactions.get(userId); }

    public void update(MessageResponse updated) {
        response.setId(updated.getId());
        response.setConversationId(updated.getConversationId());
        response.setSenderId(updated.getSenderId());
        response.setContent(updated.getContent());
        response.setFileUrl(updated.getFileUrl());
        response.setFileName(updated.getFileName());
        response.setFileSize(updated.getFileSize());
        response.setFileType(updated.getFileType());
        response.setMessageType(updated.getMessageType());
        response.setUpdatedAt(updated.getUpdatedAt());
        response.setDeleted(updated.isDeleted());
        response.setDeletedForUsers(updated.getDeletedForUsers());
        response.setStatus(updated.getStatus());
        response.setStarred(updated.isStarred());
        response.setPinned(updated.isPinned());
        response.setEditedAt(updated.getEditedAt());
        response.setReactions(updated.getReactions());
        if (!isFile) content.set(updated.getContent());
        isDeleted.set(updated.isDeleted());
        status.set(updated.getStatus() == null ? "SENT" : updated.getStatus());
        starred.set(updated.isStarred());
        pinned.set(updated.isPinned());
        setReactions(updated.getReactions());
    }

    private void setReactions(List<MessageReactionResponse> values) {
        reactions.clear();
        if (values == null) return;
        for (MessageReactionResponse reaction : values) {
            if (reaction != null && reaction.getUserId() != null
                    && reaction.getEmoji() != null) {
                reactions.put(reaction.getUserId(), reaction.getEmoji());
            }
        }
    }
}
