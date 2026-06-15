package secretchat.chatservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import secretchat.chatservice.api.mapper.MessageApiMapper;
import secretchat.chatservice.api.request.MessageStatusRequest;
import secretchat.chatservice.api.request.MessageReactionRequest;
import secretchat.chatservice.api.request.SendMessageRequest;
import secretchat.chatservice.api.request.UpdateMessageRequest;
import secretchat.chatservice.api.response.MessageResponse;
import secretchat.chatservice.application.port.in.MessageUseCase;
import secretchat.chatservice.application.port.in.MessageReactionUseCase;
import secretchat.chatservice.domain.model.Message;
import secretchat.chatservice.api.realtime.MessageRealtimePublisher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private static final Path UPLOAD_DIR = Paths.get("uploads").toAbsolutePath().normalize();
    private static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;

    private final MessageUseCase messageUseCase;
    private final MessageReactionUseCase reactionUseCase;
    private final MessageRealtimePublisher realtimePublisher;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        Message message = messageUseCase.sendMessage(MessageApiMapper.toCommand(request));
        return ResponseEntity.ok(publish(message));
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            if (file.getSize() > MAX_UPLOAD_BYTES) {
                return ResponseEntity.status(413).body(
                        "{\"error\":\"Payload Too Large\","
                                + "\"message\":\"File có dung lượng vượt quá 50 MB. "
                                + "Vui lòng chọn file nhỏ hơn để gửi.\"}");
            }
            if (!Files.exists(UPLOAD_DIR)) {
                Files.createDirectories(UPLOAD_DIR);
            }

            String fileName = java.util.UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = UPLOAD_DIR.resolve(fileName).normalize();
            Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Return a relative path using forward slashes to avoid backslash escaping issues on Windows
            return ResponseEntity.ok("uploads/" + fileName);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("File upload error: " + e.getMessage());
            return ResponseEntity.status(500).body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/history/{conversationId}")
    public ResponseEntity<List<MessageResponse>> getChatHistory(
            @PathVariable Long conversationId, 
            @RequestParam(required = false) String userId) {
        List<Message> history = messageUseCase.getChatHistory(conversationId);
        
        List<MessageResponse> responses = history.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}/recall")
    public ResponseEntity<MessageResponse> recallMessage(@PathVariable Long id, @RequestParam String userId) {
        Message message = messageUseCase.recallMessage(id, userId);
        return ResponseEntity.ok(publish(message));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessageForUser(@PathVariable Long id, @RequestParam String userId) {
        messageUseCase.deleteMessageForUser(id, userId);
        publish(messageUseCase.getMessage(id));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable Long id, @Valid @RequestBody UpdateMessageRequest request) {
        return ResponseEntity.ok(publish(
                messageUseCase.editMessage(id, request.getUserId(), request.getContent())));
    }

    @PutMapping("/{id}/star")
    public ResponseEntity<MessageResponse> setStarred(
            @PathVariable Long id, @RequestParam boolean value) {
        return ResponseEntity.ok(publish(messageUseCase.setStarred(id, value)));
    }

    @PutMapping("/{id}/pin")
    public ResponseEntity<MessageResponse> setPinned(
            @PathVariable Long id, @RequestParam boolean value) {
        return ResponseEntity.ok(publish(messageUseCase.setPinned(id, value)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<MessageResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody MessageStatusRequest request) {
        return ResponseEntity.ok(publish(
                messageUseCase.updateStatus(id, request.getUserId(), request.getStatus())));
    }

    @PutMapping("/{id}/reaction")
    public ResponseEntity<MessageResponse> setReaction(
            @PathVariable Long id,
            @Valid @RequestBody MessageReactionRequest request) {
        reactionUseCase.setReaction(id, request.getUserId(), request.getEmoji());
        return ResponseEntity.ok(publish(messageUseCase.getMessage(id)));
    }

    @GetMapping("/pinned/{conversationId}")
    public ResponseEntity<List<MessageResponse>> getPinnedMessages(@PathVariable Long conversationId) {
        return ResponseEntity.ok(messageUseCase.getPinnedMessages(conversationId).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}/around")
    public ResponseEntity<List<MessageResponse>> getMessagesAround(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(messageUseCase.getMessagesAround(id, limit).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/search/{conversationId}")
    public ResponseEntity<List<MessageResponse>> searchMessages(
            @PathVariable Long conversationId, @RequestParam String query) {
        return ResponseEntity.ok(messageUseCase.searchMessages(conversationId, query).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(@PathVariable Long id) {
        Message message = messageUseCase.getMessage(id);
        if (message.getFileUrl() == null || message.getFileUrl().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            org.springframework.core.io.Resource resource = null;
            String fileUrl = message.getFileUrl();

            if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
                resource = new org.springframework.core.io.UrlResource(new java.net.URL(fileUrl));
            } else {
                Path filePath = resolveUploadPath(fileUrl);
                if (filePath != null) {
                    org.springframework.core.io.Resource r = new org.springframework.core.io.UrlResource(filePath.toUri());
                    if (r.exists() && r.isReadable()) {
                        resource = r;
                    }
                }

                if (resource == null) {
                    String normalized = fileUrl.replace('\\', '/');
                    if (normalized.startsWith("uploads/")) {
                        normalized = normalized.substring("uploads/".length());
                    }
                    Path localPath = UPLOAD_DIR.resolve(normalized).normalize();
                    if (localPath.startsWith(UPLOAD_DIR)) {
                        org.springframework.core.io.Resource r = new org.springframework.core.io.UrlResource(localPath.toUri());
                        if (r.exists() && r.isReadable()) {
                            resource = r;
                        }
                    }
                }
            }

            if (resource == null || !resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + message.getFileName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    private Path resolveUploadPath(String fileUrl) {
        try {
            Path path = Paths.get(fileUrl);
            if (path.isAbsolute()) {
                return path;
            }

            String normalized = fileUrl.replace('\\', '/');
            if (normalized.startsWith("uploads/")) {
                normalized = normalized.substring("uploads/".length());
            }

            Path resolved = UPLOAD_DIR.resolve(normalized).normalize();
            if (resolved.startsWith(UPLOAD_DIR)) {
                return resolved;
            }
        } catch (Exception ignored) {
            // ignore invalid path and fallback
        }
        return null;
    }

    private MessageResponse publish(Message message) {
        return realtimePublisher.publish(message);
    }

    private MessageResponse toResponse(Message message) {
        MessageResponse response = MessageApiMapper.toResponse(message);
        response.setReactions(reactionUseCase.getReactions(message.getId()).stream()
                .map(reaction -> new secretchat.chatservice.api.response.MessageReactionResponse(
                        reaction.userId(), reaction.emoji()))
                .toList());
        return response;
    }
}
