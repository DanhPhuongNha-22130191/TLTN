package secretchat.auth.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.*;
import secretchat.auth.dto.request.RegisterRequest;
import secretchat.auth.dto.response.RegisterResponse;
import secretchat.auth.service.RegisterService;
import secretchat.common.exception.ApiException;
import secretchat.service.ApiClient;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class RegisterViewModel extends BaseAuthViewModel {

    private static final System.Logger LOGGER = System.getLogger(RegisterViewModel.class.getName());
    private final RegisterService registerService;

    // Inputs
    private final StringProperty fullName = new SimpleStringProperty("");
    private final StringProperty phoneNumber = new SimpleStringProperty("");
    private final StringProperty confirmPassword = new SimpleStringProperty("");
    private final BooleanProperty termsAccepted = new SimpleBooleanProperty(false);

    // Error messages
    private final StringProperty fullNameError = new SimpleStringProperty("");
    private final StringProperty phoneError = new SimpleStringProperty("");
    private final StringProperty confirmPasswordError = new SimpleStringProperty("");

    // UI States
    private final BooleanProperty registerSuccess = new SimpleBooleanProperty(false);
    private final BooleanProperty showConfirmPassword = new SimpleBooleanProperty(false);
    private final ObjectProperty<RegisterResponse> registrationResult =
            new SimpleObjectProperty<>();

    // Validation patterns
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{9,11}$");

    public RegisterViewModel() {
        this(new RegisterService(ApiClient.getInstance()));
    }

    public RegisterViewModel(RegisterService registerService) {
        this.registerService = registerService;
    }

    // Properties getters
    public StringProperty fullNameProperty() { return fullName; }
    public StringProperty phoneNumberProperty() { return phoneNumber; }
    public StringProperty confirmPasswordProperty() { return confirmPassword; }
    public BooleanProperty termsAcceptedProperty() { return termsAccepted; }

    public StringProperty fullNameErrorProperty() { return fullNameError; }
    public StringProperty phoneErrorProperty() { return phoneError; }
    public StringProperty confirmPasswordErrorProperty() { return confirmPasswordError; }

    public BooleanProperty registerSuccessProperty() { return registerSuccess; }
    public BooleanProperty showConfirmPasswordProperty() { return showConfirmPassword; }
    public ObjectProperty<RegisterResponse> registrationResultProperty() {
        return registrationResult;
    }

    public void toggleConfirmPasswordVisibility() {
        showConfirmPassword.set(!showConfirmPassword.get());
    }

    @Override
    public boolean validateInput() {
        boolean isValid = true;

        fullNameError.set("");
        usernameError.set("");
        phoneError.set("");
        passwordError.set("");
        confirmPasswordError.set("");
        globalMessage.set("");

        if (fullName.get().trim().isEmpty()) {
            fullNameError.set("Họ và tên không được để trống");
            isValid = false;
        }

        String usernameVal = username.get().trim();
        if (usernameVal.isEmpty()) {
            usernameError.set("Tên đăng nhập không được để trống");
            isValid = false;
        } else if (!USERNAME_PATTERN.matcher(usernameVal).matches()) {
            usernameError.set("Username chỉ chứa chữ cái, chữ số và dấu gạch dưới");
            isValid = false;
        }

        String phoneVal = phoneNumber.get().trim();
        if (phoneVal.isEmpty()) {
            phoneError.set("Số điện thoại không được để trống");
            isValid = false;
        } else if (!PHONE_PATTERN.matcher(phoneVal).matches()) {
            phoneError.set("Số điện thoại phải từ 9 đến 11 chữ số");
            isValid = false;
        }

        String passVal = password.get();
        if (passVal.isEmpty()) {
            passwordError.set("Mật khẩu không được để trống");
            isValid = false;
        } else if (passVal.length() < 6) {
            passwordError.set("Mật khẩu phải chứa ít nhất 6 ký tự");
            isValid = false;
        }

        String confirmPassVal = confirmPassword.get();
        if (confirmPassVal.isEmpty()) {
            confirmPasswordError.set("Vui lòng xác nhận mật khẩu");
            isValid = false;
        } else if (!confirmPassVal.equals(passVal)) {
            confirmPasswordError.set("Mật khẩu xác nhận không khớp");
            isValid = false;
        }

        if (!termsAccepted.get()) {
            globalMessage.set("Bạn phải đồng ý với điều khoản sử dụng");
            globalMessageStyle.set("-fx-text-fill: #ff4d6d;");
            isValid = false;
        }

        return isValid;
    }

    public CompletableFuture<Void> registerAsync() {
        if (!validateInput()) {
            return CompletableFuture.completedFuture(null);
        }

        isLoading.set(true);
        registerSuccess.set(false);
        registrationResult.set(null);
        globalMessage.set("Đang xử lý đăng ký...");
        globalMessageStyle.set("-fx-text-fill: #6a8dff;");

        RegisterRequest request = new RegisterRequest();
        request.setFullName(fullName.get().trim());
        request.setUsername(username.get().trim());
        request.setPhoneNumber(phoneNumber.get().trim());
        request.setPassword(password.get());
        request.setConfirmPassword(confirmPassword.get());

        return CompletableFuture.runAsync(() -> {
            try {
                RegisterResponse response = registerService.register(request);
                Platform.runLater(() -> {
                    isLoading.set(false);
                    if (response.isSuccess()) {
                        registrationResult.set(response);
                        registerSuccess.set(true);
                        globalMessage.set("Đăng ký thành công.");
                        globalMessageStyle.set("-fx-text-fill: #2e7d32;");
                    } else {
                        globalMessage.set(response.getMessage() != null ? response.getMessage() : "Đăng ký thất bại");
                        globalMessageStyle.set("-fx-text-fill: #ff4d6d;");
                    }
                });
            } catch (ApiException e) {
                Platform.runLater(() -> {
                    isLoading.set(false);
                    globalMessage.set(e.getUserMessage());
                    globalMessageStyle.set("-fx-text-fill: #ff4d6d;");
                });
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Lỗi đăng ký tài khoản", e);
                Platform.runLater(() -> {
                    isLoading.set(false);
                    globalMessage.set("Không thể kết nối server");
                    globalMessageStyle.set("-fx-text-fill: #ff4d6d;");
                });
            }
        });
    }
}
