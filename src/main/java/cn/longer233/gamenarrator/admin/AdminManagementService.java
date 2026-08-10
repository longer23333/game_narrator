package cn.longer233.gamenarrator.admin;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminManagementService {
    private final JdbcTemplate jdbc;
    private final CurrentUserContext current;
    public AdminManagementService(JdbcTemplate jdbc, CurrentUserContext current) { this.jdbc = jdbc; this.current = current; }

    public Map<String,Object> summary() {
        requireAdmin();
        return Map.of(
                "users", number("SELECT COUNT(*) FROM app_user WHERE account_type='REGISTERED'"),
                "activeUsers", number("SELECT COUNT(*) FROM app_user WHERE account_type='REGISTERED' AND status='ACTIVE'"),
                "projects", number("SELECT COUNT(*) FROM video_project WHERE deleted_at IS NULL"),
                "tasks", number("SELECT COUNT(*) FROM video_tasks"),
                "assets", number("SELECT COUNT(*) FROM media_asset WHERE deleted_at IS NULL"),
                "storedBytes", number("SELECT COALESCE(SUM(size_bytes),0) FROM media_asset WHERE deleted_at IS NULL"),
                "renderedBytes", number("SELECT COALESCE(SUM(rendered_file_size_bytes),0) FROM video_tasks"),
                "apiCostThisMonth", decimal("SELECT COALESCE(SUM(estimated_cost),0) FROM user_ai_usage_daily WHERE usage_date>=DATE_TRUNC('MONTH',CURRENT_DATE)"),
                "pendingCloudSync", number("SELECT COUNT(*) FROM cloud_sync_item WHERE sync_status IN ('PENDING','FAILED')")
        );
    }

    public List<Map<String,Object>> users() {
        requireAdmin();
        return jdbc.queryForList("SELECT u.id,u.username,u.email,u.display_name,u.role,u.status,u.storage_quota_bytes,u.api_monthly_budget,u.created_at,u.last_login_at,u.last_seen_at," +
                "(SELECT COUNT(*) FROM video_project p WHERE p.owner_id=u.id AND p.deleted_at IS NULL) project_count," +
                "(SELECT COALESCE(SUM(a.size_bytes),0) FROM media_asset a WHERE a.owner_id=u.id AND a.deleted_at IS NULL) asset_bytes " +
                "FROM app_user u WHERE u.account_type='REGISTERED' ORDER BY u.created_at DESC");
    }
    public List<Map<String,Object>> projects() {
        requireAdmin();
        return jdbc.queryForList("SELECT p.id,p.name,p.status,p.game_category,p.updated_at,u.username,t.rendered_file_size_bytes,t.created_at task_created_at " +
                "FROM video_project p JOIN app_user u ON u.id=p.owner_id LEFT JOIN video_tasks t ON t.id=p.id WHERE p.deleted_at IS NULL ORDER BY p.updated_at DESC");
    }
    public List<Map<String,Object>> usage() {
        requireAdmin();
        return jdbc.queryForList("SELECT d.usage_date,u.username,d.provider,d.model_name,d.input_tokens,d.output_tokens,d.cached_tokens,d.estimated_cost,d.request_count " +
                "FROM user_ai_usage_daily d JOIN app_user u ON u.id=d.user_id ORDER BY d.usage_date DESC,d.estimated_cost DESC LIMIT 500");
    }
    public List<Map<String,Object>> audit() { requireAdmin(); return jdbc.queryForList("SELECT l.created_at,u.username,l.action,l.target_type,l.target_id,l.detail_json FROM admin_audit_log l JOIN app_user u ON u.id=l.admin_user_id ORDER BY l.created_at DESC LIMIT 300"); }
    public List<Map<String,Object>> syncItems() { requireAdmin(); return jdbc.queryForList("SELECT s.id,u.username,s.item_type,s.local_path,s.object_key,s.size_bytes,s.sync_status,s.last_error,s.updated_at,s.synced_at FROM cloud_sync_item s JOIN app_user u ON u.id=s.user_id ORDER BY s.updated_at DESC LIMIT 500"); }

    @Transactional
    public void updateUser(UUID id, UserUpdate update) {
        requireAdmin();
        if (id.equals(current.userId()) && "DISABLED".equals(update.status())) throw new IllegalArgumentException("不能停用当前管理员账号");
        String role = update.role() == null ? null : update.role().toUpperCase();
        String status = update.status() == null ? null : update.status().toUpperCase();
        if (role != null && !Set.of("ADMIN","USER").contains(role)) throw new IllegalArgumentException("无效角色");
        if (status != null && !Set.of("ACTIVE","DISABLED").contains(status)) throw new IllegalArgumentException("无效状态");
        Long quota = update.storageQuotaBytes();
        if (quota != null && quota < 0) throw new IllegalArgumentException("存储配额不能小于 0");
        java.math.BigDecimal budget = update.apiMonthlyBudget();
        if (budget != null && budget.signum() < 0) throw new IllegalArgumentException("API 预算不能小于 0");
        int changed = jdbc.update("UPDATE app_user SET role=COALESCE(?,role),status=COALESCE(?,status),storage_quota_bytes=COALESCE(?,storage_quota_bytes),api_monthly_budget=COALESCE(?,api_monthly_budget),updated_at=? WHERE id=? AND account_type='REGISTERED'",
                role,status,quota,budget,Instant.now(),id);
        if (changed == 0) throw new IllegalArgumentException("用户不存在");
        if ("DISABLED".equals(status)) jdbc.update("UPDATE user_session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", Instant.now(), id);
        audit("UPDATE_USER","USER",id.toString(),"role="+role+",status="+status+",quota="+quota+",budget="+budget);
    }
    @Transactional public void revokeSessions(UUID id) { requireAdmin(); jdbc.update("UPDATE user_session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",Instant.now(),id); audit("REVOKE_SESSIONS","USER",id.toString(),"all active sessions revoked"); }

    private void audit(String action,String type,String id,String detail) { jdbc.update("INSERT INTO admin_audit_log(id,admin_user_id,action,target_type,target_id,detail_json,created_at) VALUES(?,?,?,?,?,?,?)", UUID.randomUUID(),current.userId(),action,type,id,"{\"summary\":\""+detail.replace("\"","'")+"\"}",Instant.now()); }
    private long number(String sql) { Number value=jdbc.queryForObject(sql,Number.class); return value==null?0:value.longValue(); }
    private java.math.BigDecimal decimal(String sql) { java.math.BigDecimal value=jdbc.queryForObject(sql,java.math.BigDecimal.class); return value==null?java.math.BigDecimal.ZERO:value; }
    private void requireAdmin() { if (!current.authenticated() || !"ADMIN".equals(current.role())) throw new AdminAccessDeniedException(); }
    public record UserUpdate(String role,String status,Long storageQuotaBytes,java.math.BigDecimal apiMonthlyBudget) {}
}
