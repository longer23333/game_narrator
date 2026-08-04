package cn.longer233.gamenarrator.identity;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Single-machine identity used until an authenticated multi-user context is introduced. */
@Component
public final class LocalUserContext implements CurrentUserContext {
    public static final UUID LOCAL_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public UUID userId() {
        return LOCAL_USER_ID;
    }
}
