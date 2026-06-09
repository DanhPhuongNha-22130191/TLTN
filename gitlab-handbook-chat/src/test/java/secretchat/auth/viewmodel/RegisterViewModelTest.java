package secretchat.auth.viewmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import secretchat.auth.dto.request.RegisterRequest;
import secretchat.auth.dto.response.RegisterResponse;
import secretchat.auth.service.RegisterService;

import static org.junit.jupiter.api.Assertions.*;

public class RegisterViewModelTest {

    private RegisterViewModel viewModel;
    private MockRegisterService mockService;

    private static class MockRegisterService extends RegisterService {
        private RegisterRequest lastRequest;
        private RegisterResponse responseToReturn = new RegisterResponse();

        public MockRegisterService() {
            super(null); // ApiClient is null since we override register
        }

        @Override
        public RegisterResponse register(RegisterRequest requestData) throws Exception {
            this.lastRequest = requestData;
            return responseToReturn;
        }
    }

    @BeforeEach
    public void setUp() {
        mockService = new MockRegisterService();
        viewModel = new RegisterViewModel(mockService);
    }

    @Test
    public void testEmptyFieldsValidation() {
        assertFalse(viewModel.validateInput(), "Validation should fail for empty inputs");
        assertFalse(viewModel.fullNameErrorProperty().get().isEmpty());
        assertFalse(viewModel.usernameErrorProperty().get().isEmpty());
    }

    @Test
    public void testInvalidEmailAndPhone() {
        viewModel.fullNameProperty().set("John Doe");
        viewModel.usernameProperty().set("johndoe");
        viewModel.emailProperty().set("invalidemail");
        viewModel.phoneNumberProperty().set("123");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");
        viewModel.termsAcceptedProperty().set(true);

        assertFalse(viewModel.validateInput(), "Validation should fail for invalid email/phone");
        assertEquals("Email không đúng định dạng", viewModel.emailErrorProperty().get());
        assertEquals("Số điện thoại phải từ 9 đến 11 chữ số", viewModel.phoneErrorProperty().get());
    }

    @Test
    public void testPasswordMismatch() {
        viewModel.fullNameProperty().set("John Doe");
        viewModel.usernameProperty().set("johndoe");
        viewModel.emailProperty().set("johndoe@example.com");
        viewModel.phoneNumberProperty().set("0912345678");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("differentpwd");
        viewModel.termsAcceptedProperty().set(true);

        assertFalse(viewModel.validateInput(), "Validation should fail for password mismatch");
        assertEquals("Mật khẩu xác nhận không khớp", viewModel.confirmPasswordErrorProperty().get());
    }

    @Test
    public void testValidInput() {
        viewModel.fullNameProperty().set("John Doe");
        viewModel.usernameProperty().set("johndoe");
        viewModel.emailProperty().set("johndoe@example.com");
        viewModel.phoneNumberProperty().set("0912345678");
        viewModel.passwordProperty().set("password123");
        viewModel.confirmPasswordProperty().set("password123");
        viewModel.termsAcceptedProperty().set(true);

        assertTrue(viewModel.validateInput(), "Validation should pass for valid inputs");
    }
}
