package cn.longer233.gamenarrator.admin;

import cn.longer233.gamenarrator.identity.LocalUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminManagementServiceTest {
    private JdbcTemplate jdbc;
    private AdminManagementService service;
    private UUID adminId;
    private UUID userId;
    private UUID projectId;
    private UUID syncId;

    @BeforeEach void setUp() {
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:admin-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(dataSource).load().migrate(); jdbc=new JdbcTemplate(dataSource);
        adminId=UUID.randomUUID(); userId=UUID.randomUUID(); projectId=UUID.randomUUID(); syncId=UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now();
        insertUser(adminId,"operator","ADMIN",now); insertUser(userId,"creator","USER",now);
        jdbc.update("INSERT INTO video_project(id,owner_id,name,game_category,commentary_style,status,created_at,updated_at,version) VALUES(?,?,?,?,?,?,?,?,0)",projectId,userId,"跨设备项目","ACTION","ANIME_THEATER","DRAFT",now,now);
        jdbc.update("INSERT INTO cloud_sync_item(id,user_id,item_type,local_id,size_bytes,sync_status,last_error,updated_at,attempt_count) VALUES(?,?,?,?,?,'FAILED','timeout',?,2)",syncId,userId,"SOURCE_MEDIA",projectId,1024,now);
        LocalUserContext current=new LocalUserContext(); current.begin(adminId,"ADMIN",true);
        service=new AdminManagementService(jdbc,current,new ObjectMapper());
    }

    @Test void providesServerSidePagesAndDetails() {
        AdminPage users=service.users("creat","ACTIVE",0,10);
        assertThat(users.total()).isEqualTo(1); assertThat(users.items().getFirst().get("username")).isEqualTo("creator");
        assertThat(service.project(projectId).get("revisions")).isNotNull();
    }

    @Test void updatesUserAndWritesStructuredAudit() {
        service.updateUser(userId,new AdminManagementService.UserUpdate("ADMIN","ACTIVE",20L*1024*1024*1024,BigDecimal.TEN));
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id=?",String.class,userId)).isEqualTo("ADMIN");
        String detail=jdbc.queryForObject("SELECT detail_json FROM admin_audit_log WHERE action='UPDATE_USER'",String.class);
        assertThat(detail).contains("\"role\":\"ADMIN\"");
    }

    @Test void archivesProjectsAndRetriesFailedSync() {
        service.setProjectArchived(projectId,true); service.retrySync(syncId);
        assertThat(jdbc.queryForObject("SELECT status FROM video_project WHERE id=?",String.class,projectId)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("SELECT sync_status FROM cloud_sync_item WHERE id=?",String.class,syncId)).isEqualTo("PENDING");
    }

    @Test void protectsCurrentAndLastAdministrator() {
        assertThatThrownBy(()->service.updateUser(adminId,new AdminManagementService.UserUpdate("USER","ACTIVE",null,null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void insertUser(UUID id,String username,String role,OffsetDateTime now) {
        jdbc.update("INSERT INTO app_user(id,username,display_name,role,status,created_at,updated_at,account_type) VALUES(?,?,?,?,?,?,?,'REGISTERED')",id,username,username,role,"ACTIVE",now,now);
    }
}
