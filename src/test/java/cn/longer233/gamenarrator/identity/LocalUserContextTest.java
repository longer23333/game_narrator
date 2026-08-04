package cn.longer233.gamenarrator.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalUserContextTest {
    @Test
    void suppliesTheSeededSingleMachineUser() {
        assertThat(new LocalUserContext().userId())
                .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }
}
