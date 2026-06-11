package secretchat.chat.viewmodel;

import org.junit.jupiter.api.Test;
import secretchat.dto.response.MessageReactionResponse;
import secretchat.dto.response.MessageResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageItemTest {

    @Test
    void initializesObservableStateFromResponse() {
        MessageResponse response = new MessageResponse();
        response.setStatus("DELIVERED");
        response.setStarred(true);
        response.setPinned(true);
        response.setReactions(List.of(reaction("user-1", "👍")));

        ChatMessageItem item = new ChatMessageItem(
                response, "An", "Xin chào", "10:30", true, false, false, false);

        assertEquals("DELIVERED", item.getStatus());
        assertTrue(item.isStarred());
        assertTrue(item.isPinned());
        assertEquals("👍", item.getReactionFor("user-1"));
    }

    @Test
    void updateRefreshesResponseAndObservableState() {
        MessageResponse response = new MessageResponse();
        response.setId("1");
        ChatMessageItem item = new ChatMessageItem(
                response, "An", "Cũ", "10:30", true, false, false, false);

        MessageResponse updated = new MessageResponse();
        updated.setId("1");
        updated.setContent("Mới");
        updated.setStatus("READ");
        updated.setDeleted(true);
        updated.setPinned(true);
        updated.setReactions(List.of(reaction("user-2", "❤️")));

        item.update(updated);

        assertEquals("Mới", item.getContent());
        assertEquals("READ", item.getStatus());
        assertTrue(item.isDeleted());
        assertTrue(item.isPinned());
        assertEquals("❤️", item.getReactionFor("user-2"));
        assertFalse(item.isDeletedForMe());
    }

    private MessageReactionResponse reaction(String userId, String emoji) {
        MessageReactionResponse reaction = new MessageReactionResponse();
        reaction.setUserId(userId);
        reaction.setEmoji(emoji);
        return reaction;
    }
}
