package secretchat.userservice.application.port;

public interface MailboxPort {
    void createMailbox(String email, String password, String displayName);
    void deleteMailbox(String email);
}
