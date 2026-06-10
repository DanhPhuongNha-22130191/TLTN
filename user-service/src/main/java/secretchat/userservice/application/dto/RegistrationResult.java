package secretchat.userservice.application.dto;

public record RegistrationResult(
        UserResult user,
        String mailboxPassword,
        String webmailUrl
) {
}
