package cn.longer233.gamenarrator.identity;

import java.util.UUID;

/** Supplies the owner/auditor identity used by application services. */
public interface CurrentUserContext {
    UUID userId();
}
