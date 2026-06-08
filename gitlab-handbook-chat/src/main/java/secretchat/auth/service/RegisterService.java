package secretchat.auth.service;

import secretchat.auth.dto.request.RegisterRequest;
import secretchat.auth.dto.response.RegisterResponse;
import secretchat.common.exception.GlobalExceptionHandler;
import secretchat.service.ApiClient;

public class RegisterService {
    private final ApiClient apiClient;

    public RegisterService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public RegisterResponse register(RegisterRequest requestData) throws Exception {
        try {
            return apiClient.post("/api/users/auth/register", requestData, RegisterResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }
}
