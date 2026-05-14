package com.tltn.chat.message.controller;

import com.tltn.chat.message.model.Conversation;
import com.tltn.chat.message.model.Message;
import com.tltn.chat.message.service.ConversationService;
import com.tltn.chat.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    // Get chat history for a conversation
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<Message>> getChatHistory(@PathVariable String conversationId) {
        return ResponseEntity.ok(messageService.getChatHistory(conversationId));
    }

    // Get all conversations for a user
    @GetMapping("/users/{userId}/conversations")
    public ResponseEntity<List<Conversation>> getUserConversations(@PathVariable String userId) {
        return ResponseEntity.ok(conversationService.getUserConversations(userId));
    }

    // Create or get group chat
    @PostMapping("/groups")
    public ResponseEntity<Conversation> createGroupChat(@RequestBody GroupChatRequest request) {
        Conversation conversation = conversationService.createGroupConversation(
                request.getName(), 
                request.getParticipantIds(), 
                request.getCreatorId());
        return ResponseEntity.ok(conversation);
    }
}

class GroupChatRequest {
    private String name;
    private List<String> participantIds;
    private String creatorId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<String> participantIds) { this.participantIds = participantIds; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
}
