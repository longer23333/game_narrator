package cn.longer233.gamenarrator.storage;

import cn.longer233.gamenarrator.admin.AdminAccessDeniedException;
import cn.longer233.gamenarrator.common.StorageCleanupService;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.observability.StorageCapacityGuard;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StorageAdminServiceAccessTest {
    @Test
    void rejectsInspectionAndCleanupBeforeTouchingStorageForNonAdmin() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StorageCapacityGuard capacity = mock(StorageCapacityGuard.class);
        StorageCleanupService cleanup = mock(StorageCleanupService.class);
        CurrentUserContext current = mock(CurrentUserContext.class);
        when(current.authenticated()).thenReturn(true);
        when(current.role()).thenReturn("USER");
        StorageAdminService service = new StorageAdminService("target/test-storage-admin", jdbc, capacity, cleanup, current);

        assertThrows(AdminAccessDeniedException.class, service::inspect);
        assertThrows(AdminAccessDeniedException.class, service::cleanupAndInspect);
        verifyNoInteractions(jdbc, capacity, cleanup);
    }
}
