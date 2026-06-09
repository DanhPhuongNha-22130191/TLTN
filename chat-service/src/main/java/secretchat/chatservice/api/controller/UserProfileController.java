package secretchat.chatservice.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import secretchat.chatservice.api.mapper.UserProfileApiMapper;
import secretchat.chatservice.api.request.CreateUserProfileRequest;
import secretchat.chatservice.api.response.UserProfileResponse;
import secretchat.chatservice.application.port.in.UserProfileUseCase;
import secretchat.chatservice.domain.model.UserProfile;

import java.util.Enumeration;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileUseCase userProfileUseCase;

    @GetMapping("/test")
    public String test(HttpServletRequest request) {

        Enumeration<String> names = request.getHeaderNames();

        while (names.hasMoreElements()) {
            String name = names.nextElement();
            System.out.println(name + " = " + request.getHeader(name));
        }

        return "ok";
    }

    @PostMapping("/profile")
    public ResponseEntity<UserProfileResponse> createOrUpdateProfile(
            @Valid @RequestBody CreateUserProfileRequest request) {
        UserProfile profile = UserProfileApiMapper.toDomain(request);
        UserProfile saved = userProfileUseCase.createOrUpdateProfile(profile);
        return ResponseEntity.ok(UserProfileApiMapper.toResponse(saved));
    }

    @GetMapping("/profile/user/{userId}")
    public ResponseEntity<UserProfileResponse> getProfileById(@PathVariable String userId) {
        return userProfileUseCase.getProfileById(userId)
                .map(profile -> ResponseEntity.ok(UserProfileApiMapper.toResponse(profile)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserProfileResponse> getProfileByUsername(@PathVariable String username) {
        return userProfileUseCase.getProfileByUsername(username)
                .map(profile -> ResponseEntity.ok(UserProfileApiMapper.toResponse(profile)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/external/{externalSub}")
    public ResponseEntity<UserProfileResponse> getProfileByExternalSub(@PathVariable String externalSub) {
        return userProfileUseCase.getProfileByExternalSub(externalSub)
                .map(profile -> ResponseEntity.ok(UserProfileApiMapper.toResponse(profile)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
