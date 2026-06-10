package secretchat.userservice.domain.exception;

public class MailboxException extends RuntimeException {
    public MailboxException(String message) {
        super(message);
    }

    public MailboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
