package secretchat.chatservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import secretchat.chatservice.api.mapper.ConversationApiMapper;
import secretchat.chatservice.api.request.CreatePersonalConversationRequest;
import secretchat.chatservice.api.response.ConversationResponse;
import secretchat.chatservice.application.port.in.ConversationUseCase;
import secretchat.chatservice.domain.model.Conversation;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationUseCase conversationUseCase;

    @PostMapping("/personal")
    public ResponseEntity<ConversationResponse> createPersonalConversation(
            @Valid @RequestBody CreatePersonalConversationRequest request) {
        Conversation conversation = conversationUseCase.createPersonalConversation(
                request.getSenderId(),
                request.getReceiverId()
        );
        return ResponseEntity.ok(ConversationApiMapper.toResponse(conversation));
    }

    @PostMapping("/group/{groupId}")
    public ResponseEntity<ConversationResponse> createGroupConversation(@PathVariable Long groupId) {
        Conversation conversation = conversationUseCase.createGroupConversation(groupId);
        return ResponseEntity.ok(ConversationApiMapper.toResponse(conversation));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable Long id) {
        Conversation conversation = conversationUseCase.getConversation(id);
        return ResponseEntity.ok(ConversationApiMapper.toResponse(conversation));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ConversationResponse>> getUserConversations(@PathVariable String userId) {
        List<Conversation> conversations = conversationUseCase.getUserConversations(userId);
        List<ConversationResponse> responses = conversations.stream()
                .map(ConversationApiMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ConversationResponse> markAsRead(@PathVariable Long id) {
        Conversation conversation = conversationUseCase.markAsRead(id);
        return ResponseEntity.ok(ConversationApiMapper.toResponse(conversation));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id) {
        conversationUseCase.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }
}
