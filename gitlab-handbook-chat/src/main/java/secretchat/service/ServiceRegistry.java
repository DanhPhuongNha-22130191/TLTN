package secretchat.service;

import secretchat.auth.service.LoginService;
import secretchat.auth.service.RegisterService;
import secretchat.chat.service.ChatService;

import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated Sử dụng Constructor Injection trực tiếp thay thế.
 * <p>
 * Class này là Service Locator (anti-pattern) và không nên được sử dụng trong code mới.
 * Thay thế bằng cách inject dependency qua constructor:
 * <pre>
 *   // Thay vì:
 *   ServiceRegistry.getInstance().get(ChatService.class)
 *
 *   // Hãy dùng:
 *   new ChatService(ApiClient.getInstance())
 * </pre>
 * Trong tương lai sẽ xem xét xóa class này khi tất cả phụ thuộc đã được migrate.
 */
@Deprecated(forRemoval = true)
public class ServiceRegistry {
    private static final ServiceRegistry INSTANCE = new ServiceRegistry();
    private final Map<Class<?>, Object> services = new HashMap<>();

    private ServiceRegistry() {
        // Infrastructure
        register(ApiClient.class, ApiClient.getInstance());

        // Auth services
        register(RegisterService.class, new RegisterService(get(ApiClient.class)));
        register(LoginService.class,    new LoginService(get(ApiClient.class)));

        // Connection / health
        register(ConnectionStatusService.class, new ConnectionStatusService());

        // Chat services
        register(ChatService.class, new ChatService(get(ApiClient.class)));
    }

    public static ServiceRegistry getInstance() {
        return INSTANCE;
    }

    public <T> void register(Class<T> serviceClass, T implementation) {
        services.put(serviceClass, implementation);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> serviceClass) {
        T service = (T) services.get(serviceClass);
        if (service == null) {
            throw new IllegalArgumentException("No service registered for " + serviceClass.getName());
        }
        return service;
    }
}
