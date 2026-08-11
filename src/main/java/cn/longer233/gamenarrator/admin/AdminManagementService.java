package cn.longer233.gamenarrator.admin;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AdminManagementService {
    private static final Set<String> USER_STATES = Set.of("ACTIVE","DISABLED");
    private static final Set<String> PROJECT_STATES = Set.of("DRAFT","PROCESSING","READY","FAILED","ARCHIVED");
    private static final Set<String> SYNC_STATES = Set.of("LOCAL_ONLY","PENDING","SYNCED","FAILED");
    private final JdbcTemplate jdbc;
    private final CurrentUserContext current;
    private final ObjectMapper mapper;

    public AdminManagementService(JdbcTemplate jdbc, CurrentUserContext current, ObjectMapper mapper) {
        this.jdbc=jdbc; this.current=current; this.mapper=mapper;
    }

    public Map<String,Object> overview() {
        requireAdmin();
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        LocalDate trends = LocalDate.now().minusDays(13);
        Map<String,Object> summary = orderedMap(
                "users",number("SELECT COUNT(*) FROM app_user WHERE account_type='REGISTERED'"),
                "activeUsers",number("SELECT COUNT(*) FROM app_user WHERE account_type='REGISTERED' AND status='ACTIVE'"),
                "activeSessions",number("SELECT COUNT(*) FROM user_session WHERE revoked_at IS NULL AND expires_at>?",Instant.now()),
                "projects",number("SELECT COUNT(*) FROM video_project WHERE deleted_at IS NULL"),
                "failedProjects",number("SELECT COUNT(*) FROM video_project WHERE deleted_at IS NULL AND status='FAILED'"),
                "storedBytes",number("SELECT COALESCE(SUM(size_bytes),0) FROM source_media_storage"),
                "renderedBytes",number("SELECT COALESCE(SUM(rendered_file_size_bytes),0) FROM video_tasks"),
                "apiCostThisMonth",decimal("SELECT COALESCE(SUM(estimated_cost),0) FROM user_ai_usage_daily WHERE usage_date>=?",month),
                "pendingCloudSync",number("SELECT COUNT(*) FROM cloud_sync_item WHERE sync_status IN ('PENDING','FAILED')")
        );
        return orderedMap("summary",summary,
                "userStatus",rows("SELECT status,COUNT(*) count FROM app_user WHERE account_type='REGISTERED' GROUP BY status ORDER BY status"),
                "projectStatus",rows("SELECT status,COUNT(*) count FROM video_project WHERE deleted_at IS NULL GROUP BY status ORDER BY status"),
                "syncStatus",rows("SELECT sync_status status,COUNT(*) count FROM cloud_sync_item GROUP BY sync_status ORDER BY sync_status"),
                "dailyUsage",rows("SELECT usage_date,SUM(request_count) request_count,SUM(input_tokens+output_tokens) token_count,SUM(estimated_cost) estimated_cost FROM user_ai_usage_daily WHERE usage_date>=? GROUP BY usage_date ORDER BY usage_date",trends),
                "recentAudit",rows("SELECT l.created_at,u.username,l.action,l.target_type,l.target_id FROM admin_audit_log l JOIN app_user u ON u.id=l.admin_user_id ORDER BY l.created_at DESC LIMIT 10"));
    }

    public AdminPage users(String search,String status,int page,int size) {
        requireAdmin(); PageSpec spec=page(page,size); String term=like(search); String state=enumValue(status,USER_STATES);
        String where=" WHERE u.account_type='REGISTERED' AND (?='' OR LOWER(u.username) LIKE ? OR LOWER(COALESCE(u.email,'')) LIKE ? OR LOWER(u.display_name) LIKE ?) AND (?='' OR u.status=?) ";
        Object[] args={normalized(search),term,term,term,state,state};
        long total=count("SELECT COUNT(*) FROM app_user u"+where,args);
        List<Map<String,Object>> items=rows("SELECT u.id,u.username,u.email,u.display_name,u.role,u.status,u.storage_quota_bytes,u.api_monthly_budget,u.created_at,u.last_login_at,u.last_seen_at,"+
                "(SELECT COUNT(*) FROM video_project p WHERE p.owner_id=u.id AND p.deleted_at IS NULL) project_count,"+
                "(SELECT COALESCE(SUM(s.size_bytes),0) FROM source_media_storage s JOIN video_tasks t ON t.id=s.task_id WHERE t.owner_id=u.id) asset_bytes,"+
                "(SELECT COUNT(*) FROM user_session x WHERE x.user_id=u.id AND x.revoked_at IS NULL AND x.expires_at>CURRENT_TIMESTAMP) active_sessions "+
                "FROM app_user u"+where+"ORDER BY u.created_at DESC LIMIT ? OFFSET ?",append(args,spec.size(),spec.offset()));
        return AdminPage.of(items,total,spec.page(),spec.size());
    }

    public Map<String,Object> user(UUID id) {
        requireAdmin();
        Map<String,Object> account=one("SELECT id,username,email,display_name,role,status,storage_quota_bytes,api_monthly_budget,created_at,last_login_at,last_seen_at FROM app_user WHERE id=? AND account_type='REGISTERED'",id);
        List<Map<String,Object>> sessions=rows("SELECT id,client_name,created_at,last_seen_at,expires_at,revoked_at FROM user_session WHERE user_id=? ORDER BY last_seen_at DESC LIMIT 20",id);
        List<Map<String,Object>> projects=rows("SELECT id,name,status,updated_at FROM video_project WHERE owner_id=? AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT 10",id);
        return orderedMap("account",account,"sessions",sessions,"recentProjects",projects);
    }

    public AdminPage projects(String search,String status,int page,int size) {
        requireAdmin(); PageSpec spec=page(page,size); String term=like(search); String state=enumValue(status,PROJECT_STATES);
        String where=" WHERE p.deleted_at IS NULL AND (?='' OR LOWER(p.name) LIKE ? OR LOWER(u.username) LIKE ?) AND (?='' OR p.status=?) ";
        Object[] args={normalized(search),term,term,state,state}; long total=count("SELECT COUNT(*) FROM video_project p JOIN app_user u ON u.id=p.owner_id"+where,args);
        List<Map<String,Object>> items=rows("SELECT p.id,p.name,p.status,p.game_category,p.commentary_style,p.version,p.created_at,p.updated_at,u.username,"+
                "t.rendered_file_size_bytes,t.duration_seconds,t.failure_reason,"+
                "(SELECT COUNT(*) FROM project_revision r WHERE r.project_id=p.id) revision_count,"+
                "(SELECT COUNT(*) FROM cloud_sync_item s WHERE s.user_id=p.owner_id AND s.local_id=p.id AND s.sync_status='SYNCED') synced_sources "+
                "FROM video_project p JOIN app_user u ON u.id=p.owner_id LEFT JOIN video_tasks t ON t.id=p.id"+where+"ORDER BY p.updated_at DESC LIMIT ? OFFSET ?",append(args,spec.size(),spec.offset()));
        return AdminPage.of(items,total,spec.page(),spec.size());
    }

    public Map<String,Object> project(UUID id) {
        requireAdmin();
        Map<String,Object> project=one("SELECT p.*,u.username,t.duration_seconds,t.rendered_file_size_bytes,t.failure_reason FROM video_project p JOIN app_user u ON u.id=p.owner_id LEFT JOIN video_tasks t ON t.id=p.id WHERE p.id=? AND p.deleted_at IS NULL",id);
        List<Map<String,Object>> revisions=rows("SELECT id,revision_no,change_type,change_summary,created_at FROM project_revision WHERE project_id=? ORDER BY revision_no DESC LIMIT 20",id);
        List<Map<String,Object>> runs=rows("SELECT id,status,run_type,started_at,finished_at,elapsed_ms,failure_message FROM generation_run WHERE project_id=? ORDER BY created_at DESC LIMIT 20",id);
        List<Map<String,Object>> artifacts=rows("SELECT id,artifact_type,mime_type,size_bytes,sha256,temporary,created_at FROM artifact WHERE project_id=? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 30",id);
        return orderedMap("project",project,"revisions",revisions,"runs",runs,"artifacts",artifacts);
    }

    public Map<String,Object> usage(LocalDate from,LocalDate to,String search,int page,int size) {
        requireAdmin(); LocalDate end=to==null?LocalDate.now():to; LocalDate start=from==null?end.minusDays(29):from;
        if(start.isAfter(end)||ChronoUnit.DAYS.between(start,end)>366) throw new IllegalArgumentException("时间范围必须在 0 到 366 天内");
        PageSpec spec=page(page,size); String term=like(search); Object[] args={start,end,normalized(search),term,term,term};
        String where=" WHERE d.usage_date BETWEEN ? AND ? AND (?='' OR LOWER(u.username) LIKE ? OR LOWER(d.provider) LIKE ? OR LOWER(d.model_name) LIKE ?) ";
        long total=count("SELECT COUNT(*) FROM user_ai_usage_daily d JOIN app_user u ON u.id=d.user_id"+where,args);
        List<Map<String,Object>> items=rows("SELECT d.usage_date,u.username,d.provider,d.model_name,d.input_tokens,d.output_tokens,d.cached_tokens,d.estimated_cost,d.request_count FROM user_ai_usage_daily d JOIN app_user u ON u.id=d.user_id"+where+"ORDER BY d.usage_date DESC,d.estimated_cost DESC LIMIT ? OFFSET ?",append(args,spec.size(),spec.offset()));
        Map<String,Object> totals=row("SELECT COALESCE(SUM(d.request_count),0) request_count,COALESCE(SUM(d.input_tokens),0) input_tokens,COALESCE(SUM(d.output_tokens),0) output_tokens,COALESCE(SUM(d.cached_tokens),0) cached_tokens,COALESCE(SUM(d.estimated_cost),0) estimated_cost FROM user_ai_usage_daily d JOIN app_user u ON u.id=d.user_id"+where,args);
        return orderedMap("page",AdminPage.of(items,total,spec.page(),spec.size()),"totals",totals,"from",start,"to",end);
    }

    public AdminPage syncItems(String search,String status,int page,int size) {
        requireAdmin(); PageSpec spec=page(page,size); String term=like(search); String state=enumValue(status,SYNC_STATES);
        String where=" WHERE (?='' OR LOWER(u.username) LIKE ? OR LOWER(COALESCE(s.object_key,'')) LIKE ? OR LOWER(s.item_type) LIKE ?) AND (?='' OR s.sync_status=?) ";
        Object[] args={normalized(search),term,term,term,state,state}; long total=count("SELECT COUNT(*) FROM cloud_sync_item s JOIN app_user u ON u.id=s.user_id"+where,args);
        List<Map<String,Object>> items=rows("SELECT s.id,u.username,s.item_type,s.local_id,s.object_key,s.size_bytes,s.sync_status,s.attempt_count,s.last_error,s.next_attempt_at,s.last_attempt_at,s.updated_at,s.synced_at FROM cloud_sync_item s JOIN app_user u ON u.id=s.user_id"+where+"ORDER BY s.updated_at DESC LIMIT ? OFFSET ?",append(args,spec.size(),spec.offset()));
        return AdminPage.of(items,total,spec.page(),spec.size());
    }

    public AdminPage audit(String search,String action,int page,int size) {
        requireAdmin(); PageSpec spec=page(page,size); String term=like(search); String act=normalized(action).toUpperCase(Locale.ROOT);
        String where=" WHERE (?='' OR LOWER(u.username) LIKE ? OR LOWER(COALESCE(l.target_id,'')) LIKE ? OR LOWER(l.target_type) LIKE ?) AND (?='' OR l.action=?) ";
        Object[] args={normalized(search),term,term,term,act,act}; long total=count("SELECT COUNT(*) FROM admin_audit_log l JOIN app_user u ON u.id=l.admin_user_id"+where,args);
        List<Map<String,Object>> items=rows("SELECT l.id,l.created_at,u.username,l.action,l.target_type,l.target_id,l.detail_json FROM admin_audit_log l JOIN app_user u ON u.id=l.admin_user_id"+where+"ORDER BY l.created_at DESC LIMIT ? OFFSET ?",append(args,spec.size(),spec.offset()));
        return AdminPage.of(items,total,spec.page(),spec.size());
    }

    @Transactional public void updateUser(UUID id,UserUpdate update) {
        requireAdmin(); String role=upper(update.role()); String status=upper(update.status());
        if(id.equals(current.userId())&&("DISABLED".equals(status)||"USER".equals(role))) throw new IllegalArgumentException("不能停用当前管理员或取消自己的管理员权限");
        if(role!=null&&!Set.of("ADMIN","USER").contains(role)) throw new IllegalArgumentException("无效角色");
        if(status!=null&&!USER_STATES.contains(status)) throw new IllegalArgumentException("无效状态");
        if(("USER".equals(role)||"DISABLED".equals(status))&&isLastActiveAdmin(id)) throw new IllegalArgumentException("系统必须保留至少一个可登录管理员");
        int changed=jdbc.update("UPDATE app_user SET role=COALESCE(?,role),status=COALESCE(?,status),storage_quota_bytes=COALESCE(?,storage_quota_bytes),api_monthly_budget=COALESCE(?,api_monthly_budget),updated_at=? WHERE id=? AND account_type='REGISTERED'",role,status,update.storageQuotaBytes(),update.apiMonthlyBudget(),Instant.now(),id);
        if(changed==0) throw new IllegalArgumentException("用户不存在");
        if("DISABLED".equals(status)) jdbc.update("UPDATE user_session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",Instant.now(),id);
        audit("UPDATE_USER","USER",id,Map.of("role",nullable(role),"status",nullable(status),"quota",nullable(update.storageQuotaBytes()),"budget",nullable(update.apiMonthlyBudget())));
    }
    @Transactional public void revokeSessions(UUID id) { requireAdmin(); int changed=jdbc.update("UPDATE user_session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",Instant.now(),id); audit("REVOKE_SESSIONS","USER",id,Map.of("revokedSessions",changed)); }
    @Transactional public void setProjectArchived(UUID id,boolean archived) { requireAdmin(); String status=archived?"ARCHIVED":"DRAFT"; int changed=jdbc.update("UPDATE video_project SET status=?,updated_at=?,version=version+1 WHERE id=? AND deleted_at IS NULL AND status NOT IN ('PROCESSING')",status,OffsetDateTime.now(ZoneOffset.UTC),id); if(changed==0) throw new IllegalArgumentException("项目不存在、正在处理或状态未改变"); audit(archived?"ARCHIVE_PROJECT":"RESTORE_PROJECT","PROJECT",id,Map.of("status",status)); }
    @Transactional public void retrySync(UUID id) { requireAdmin(); int changed=jdbc.update("UPDATE cloud_sync_item SET sync_status='PENDING',attempt_count=0,last_error=NULL,next_attempt_at=NULL,updated_at=? WHERE id=? AND sync_status IN ('FAILED','PERMANENT_FAILURE','LOCAL_ONLY')",OffsetDateTime.now(ZoneOffset.UTC),id); if(changed==0) throw new IllegalArgumentException("同步项不存在或当前状态不可重试"); audit("RETRY_CLOUD_SYNC","CLOUD_SYNC",id,Map.of("status","PENDING")); }

    private void audit(String action,String type,UUID id,Map<String,Object> detail) { try { jdbc.update("INSERT INTO admin_audit_log(id,admin_user_id,action,target_type,target_id,detail_json,created_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),current.userId(),action,type,id.toString(),mapper.writeValueAsString(detail),Instant.now()); } catch(Exception e){ throw new IllegalStateException("无法写入审计日志",e); } }
    private Map<String,Object> one(String sql,Object...args){ List<Map<String,Object>> values=rows(sql,args); if(values.isEmpty()) throw new IllegalArgumentException("记录不存在"); return values.getFirst(); }
    private List<Map<String,Object>> rows(String sql,Object...args){ return jdbc.queryForList(sql,args).stream().map(this::lowerKeys).toList(); }
    private Map<String,Object> row(String sql,Object...args){ return lowerKeys(jdbc.queryForMap(sql,args)); }
    private Map<String,Object> lowerKeys(Map<String,Object> source){ LinkedHashMap<String,Object> result=new LinkedHashMap<>(); source.forEach((key,value)->result.put(key.toLowerCase(Locale.ROOT),value)); return result; }
    private long count(String sql,Object...args){ Number n=jdbc.queryForObject(sql,Number.class,args); return n==null?0:n.longValue(); }
    private long number(String sql,Object...args){ return count(sql,args); }
    private BigDecimal decimal(String sql,Object...args){ BigDecimal n=jdbc.queryForObject(sql,BigDecimal.class,args); return n==null?BigDecimal.ZERO:n; }
    private PageSpec page(int page,int size){ int safeSize=Math.max(10,Math.min(100,size)); return new PageSpec(Math.max(0,page),safeSize); }
    private String normalized(String value){ return value==null?"":value.trim().toLowerCase(Locale.ROOT); }
    private String like(String value){ return "%"+normalized(value)+"%"; }
    private String enumValue(String value,Set<String> allowed){ String result=value==null?"":value.trim().toUpperCase(Locale.ROOT); if(!result.isEmpty()&&!allowed.contains(result)) throw new IllegalArgumentException("筛选状态无效"); return result; }
    private String upper(String value){ return value==null||value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT); }
    private Object[] append(Object[] values,Object...tail){ Object[] result=Arrays.copyOf(values,values.length+tail.length); System.arraycopy(tail,0,result,values.length,tail.length); return result; }
    private Object nullable(Object value){ return value==null?"UNCHANGED":value; }
    private void requireAdmin(){ if(!current.authenticated()||!"ADMIN".equals(current.role())) throw new AdminAccessDeniedException(); }
    private boolean isLastActiveAdmin(UUID id){ Integer target=jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Integer.class,id); Integer active=jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE role='ADMIN' AND status='ACTIVE' AND account_type='REGISTERED'",Integer.class); return target!=null&&target>0&&active!=null&&active<=1; }
    private Map<String,Object> orderedMap(Object...pairs){ LinkedHashMap<String,Object> map=new LinkedHashMap<>(); for(int i=0;i<pairs.length;i+=2) map.put(String.valueOf(pairs[i]),pairs[i+1]); return map; }
    private record PageSpec(int page,int size){ int offset(){return page*size;} }
    public record UserUpdate(String role,String status,@Min(0) Long storageQuotaBytes,@DecimalMin("0") BigDecimal apiMonthlyBudget) {}
}
