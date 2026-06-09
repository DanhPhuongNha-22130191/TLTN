package secretchat.userservice.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username chỉ chứa chữ cái, chữ số và dấu gạch dưới")
        @Size(min = 3, max = 50, message = "Username phải từ 3 đến 50 ký tự")
        String username,
        String fullName,
        String avatar,
        @Pattern(regexp = "^\\d{9,11}$", message = "Số điện thoại phải từ 9 đến 11 chữ số")
        String phoneNumber,
        @Size(min = 6, max = 100, message = "Mật khẩu mới phải có ít nhất 6 ký tự")
        String newPassword
) {}
