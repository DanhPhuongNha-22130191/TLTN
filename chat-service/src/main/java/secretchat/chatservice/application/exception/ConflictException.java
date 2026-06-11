package secretchat.chatservice.application.exception;

public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(message);
    }
}
