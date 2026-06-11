package secretchat.chatservice.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import secretchat.chatservice.application.port.in.UserProfileUseCase;
import secretchat.chatservice.application.service.PresenceService;
import secretchat.chatservice.domain.model.UserProfile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {
    private final PresenceService presenceService;
    private final UserProfileUseCase userProfileUseCase;

    @PostMapping("/heartbeat")
    public ResponseEntity<PresenceService.PresenceStatus> heartbeat(
            @AuthenticationPrincipal Jwt jwt) {
        UserProfile profile = userProfileUseCase.getProfileByExternalSub(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy hồ sơ chat của người dùng hiện tại."));
        return ResponseEntity.ok(presenceService.heartbeat(profile.getId()));
    }

    @GetMapping
    public ResponseEntity<Map<String, PresenceService.PresenceStatus>> getStatuses(
            @RequestParam List<String> userIds) {
        return ResponseEntity.ok(presenceService.getStatuses(userIds));
    }
}
