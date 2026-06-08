package secretchat.auth.viewmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import secretchat.auth.dto.request.LoginRequest;
import secretchat.auth.dto.response.LoginResponse;
import secretchat.auth.service.LoginService;

import static org.junit.jupiter.api.Assertions.*;

public class LoginViewModelTest {

    private LoginViewModel viewModel;
    private MockLoginService mockService;

    private static class MockLoginService extends LoginService {
        private LoginRequest lastRequest;
        private LoginResponse responseToReturn = new LoginResponse();

        public MockLoginService() {
            super(null); // ApiClient is null since we override login
        }

        @Override
        public LoginResponse login(LoginRequest requestData) throws Exception {
            this.lastRequest = requestData;
            return responseToReturn;
        }
    }

    @BeforeEach
    public void setUp() {
        mockService = new MockLoginService();
        viewModel = new LoginViewModel(mockService);
    }

    @Test
    public void testEmptyFieldsValidation() {
        assertFalse(viewModel.validateInput(), "Validation should fail for empty fields");
        assertFalse(viewModel.usernameErrorProperty().get().isEmpty());
        assertFalse(viewModel.passwordErrorProperty().get().isEmpty());
    }

    @Test
    public void testEmptyUsername() {
        viewModel.usernameProperty().set("");
        viewModel.passwordProperty().set("password123");
        assertFalse(viewModel.validateInput());
        assertEquals("Tên đăng nhập không được để trống", viewModel.usernameErrorProperty().get());
        assertTrue(viewModel.passwordErrorProperty().get().isEmpty());
    }

    @Test
    public void testEmptyPassword() {
        viewModel.usernameProperty().set("johndoe");
        viewModel.passwordProperty().set("");
        assertFalse(viewModel.validateInput());
        assertTrue(viewModel.usernameErrorProperty().get().isEmpty());
        assertEquals("Mật khẩu không được để trống", viewModel.passwordErrorProperty().get());
    }

    @Test
    public void testValidInput() {
        viewModel.usernameProperty().set("johndoe");
        viewModel.passwordProperty().set("password123");
        assertTrue(viewModel.validateInput());
    }
}
