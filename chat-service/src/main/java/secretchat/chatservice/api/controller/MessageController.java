package secretchat.chatservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import secretchat.chatservice.api.mapper.MessageApiMapper;
import secretchat.chatservice.api.request.SendMessageRequest;
import secretchat.chatservice.api.response.MessageResponse;
import secretchat.chatservice.application.port.in.MessageUseCase;
import secretchat.chatservice.domain.model.Message;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageUseCase messageUseCase;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        Message message = messageUseCase.sendMessage(MessageApiMapper.toCommand(request));
        return ResponseEntity.ok(MessageApiMapper.toResponse(message));
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            java.io.File dir = new java.io.File("uploads");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = java.util.UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            java.nio.file.Path filePath = java.nio.file.Paths.get("uploads", fileName);
            java.nio.file.Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // Return a relative path using forward slashes to avoid backslash escaping issues on Windows
            return ResponseEntity.ok("uploads/" + fileName);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/history/{conversationId}")
    public ResponseEntity<List<MessageResponse>> getChatHistory(
            @PathVariable Long conversationId, 
            @RequestParam(required = false) String userId) {
        List<Message> history = messageUseCase.getChatHistory(conversationId);
        
        List<MessageResponse> responses = history.stream()
                .map(MessageApiMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}/recall")
    public ResponseEntity<MessageResponse> recallMessage(@PathVariable Long id, @RequestParam String userId) {
        Message message = messageUseCase.recallMessage(id, userId);
        return ResponseEntity.ok(MessageApiMapper.toResponse(message));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessageForUser(@PathVariable Long id, @RequestParam String userId) {
        messageUseCase.deleteMessageForUser(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(@PathVariable Long id) {
        Message message = messageUseCase.getMessage(id);
        if (message.getFileUrl() == null || message.getFileUrl().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            org.springframework.core.io.Resource resource = null;
            if (message.getFileUrl().startsWith("http://") || message.getFileUrl().startsWith("https://")) {
                resource = new org.springframework.core.io.UrlResource(new java.net.URL(message.getFileUrl()));
            } else {
                // Try the path as stored first
                try {
                    java.nio.file.Path filePath = java.nio.file.Paths.get(message.getFileUrl());
                    org.springframework.core.io.Resource r = new org.springframework.core.io.UrlResource(filePath.toUri());
                    if (r.exists() && r.isReadable()) {
                        resource = r;
                    }
                } catch (Exception ex) {
                    // Ignore and try fallback
                }
                
                // Fallback: If it's a file path but didn't exist/read or failed to parse (e.g. OS path mismatches),
                // extract only the filename and look it up directly in the "uploads" directory
                if (resource == null) {
                    String fileUrl = message.getFileUrl();
                    String filename = fileUrl;
                    int lastSlash = fileUrl.lastIndexOf('/');
                    int lastBackslash = fileUrl.lastIndexOf('\\');
                    int maxIdx = Math.max(lastSlash, lastBackslash);
                    if (maxIdx >= 0 && maxIdx < fileUrl.length() - 1) {
                        filename = fileUrl.substring(maxIdx + 1);
                    }
                    java.nio.file.Path localPath = java.nio.file.Paths.get("uploads", filename);
                    org.springframework.core.io.Resource r = new org.springframework.core.io.UrlResource(localPath.toUri());
                    if (r.exists() && r.isReadable()) {
                        resource = r;
                    }
                }
            }
            
            if (resource == null || !resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + message.getFileName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
