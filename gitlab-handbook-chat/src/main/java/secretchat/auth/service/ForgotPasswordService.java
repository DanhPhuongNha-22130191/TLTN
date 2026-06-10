package secretchat.auth.service;

import secretchat.auth.dto.request.ForgotPasswordRequest;
import secretchat.auth.dto.response.ForgotPasswordResponse;
import secretchat.common.exception.GlobalExceptionHandler;
import secretchat.service.ApiClient;

public class ForgotPasswordService {
    private final ApiClient apiClient;

    public ForgotPasswordService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ForgotPasswordResponse requestReset(String email) throws Exception {
        try {
            return apiClient.post(
                    "/api/users/auth/forgot-password",
                    new ForgotPasswordRequest(email),
                    ForgotPasswordResponse.class);
        } catch (Exception error) {
            throw GlobalExceptionHandler.handle(error);
        }
    }
}
