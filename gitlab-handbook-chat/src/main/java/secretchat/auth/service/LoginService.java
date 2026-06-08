package secretchat.auth.service;

import secretchat.auth.dto.request.LoginRequest;
import secretchat.auth.dto.response.LoginResponse;
import secretchat.common.exception.ApiException;
import secretchat.common.exception.GlobalExceptionHandler;
import secretchat.service.ApiClient;

public class LoginService {
    private final ApiClient apiClient;

    public LoginService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public LoginResponse login(LoginRequest requestData) throws Exception {
        try {
            return apiClient.post("/api/users/auth/login", requestData, LoginResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }
}
