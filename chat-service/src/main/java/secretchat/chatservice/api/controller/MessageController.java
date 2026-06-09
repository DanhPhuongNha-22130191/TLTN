package secretchat.chatservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import secretchat.chatservice.api.mapper.MessageApiMapper;
import secretchat.chatservice.api.request.SendMessageRequest;
import secretchat.chatservice.api.response.MessageResponse;
import secretchat.chatservice.application.port.in.MessageUseCase;
import secretchat.chatservice.domain.model.Message;

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

    private final MessageUseCase messageUseCase;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        Message message = messageUseCase.sendMessage(MessageApiMapper.toCommand(request));
        return ResponseEntity.ok(MessageApiMapper.toResponse(message));
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
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
}
