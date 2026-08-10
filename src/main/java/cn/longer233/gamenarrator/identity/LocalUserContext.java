package cn.longer233.gamenarrator.identity;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Request identity with anonymous-local fallback for offline desktop use. */
@Component
public final class LocalUserContext implements CurrentUserContext {
    public static final UUID LOCAL_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ThreadLocal<Identity> requestIdentity = new ThreadLocal<>();

    @Override
    public UUID userId() {
        Identity value = requestIdentity.get();
        return value == null ? LOCAL_USER_ID : value.userId();
    }

    @Override public String role() {
        Identity value = requestIdentity.get();
        return value == null ? "USER" : value.role();
    }
    @Override public boolean authenticated() {
        Identity value = requestIdentity.get();
        return value != null && value.authenticated();
    }
    public void begin(UUID userId, String role, boolean authenticated) {
        requestIdentity.set(new Identity(userId == null ? LOCAL_USER_ID : userId,
                role == null ? "USER" : role, authenticated));
    }
    public void begin(UUID userId) { begin(userId, "USER", false); }
    public void clear() { requestIdentity.remove(); }
    private record Identity(UUID userId, String role, boolean authenticated) {}
}
