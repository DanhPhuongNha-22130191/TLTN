package secretchat.chat.viewmodel;

import secretchat.service.SessionManager;

/**
 * ViewModel for the main chat screen.
 * Holds session state (access token) and chat-related observable data.
 */
public class MainViewModel {

    public void logout() {
        SessionManager.getInstance().clear();
    }
}
