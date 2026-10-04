package com.inkspace.common.audit;

import com.inkspace.common.security.AuthUser;
import com.inkspace.domain.entity.AuditLog;
import com.inkspace.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计日志写入入口：供 AOP 切面与异步/流式接口共用。
 *
 * 关键约束：SecurityContextHolder 与 RequestContextHolder 都是 ThreadLocal，
 * 必须在请求线程里先调用 {@link #capture()} 取快照，再在异步线程里
 * {@link AuditContext#write(String, String, Long, String)}，否则异步线程取不到用户身份。
 */
@Component
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditLogMapper auditLogMapper;

    public AuditRecorder(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 在请求线程捕获审计上下文；失败不影响主流程。 */
    public AuditContext capture() {
        try {
            return new AuditContext(currentUserId(), currentIp());
        } catch (Exception e) {
            log.debug("捕获审计上下文失败", e);
            return new AuditContext(null, "");
        }
    }

    /** 供切面复用同一个 Mapper 实例写日志。 */
    public AuditLogMapper mapper() {
        return auditLogMapper;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getId();
        }
        return null;
    }

    private String currentIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 跨线程安全的审计上下文快照。 */
    public record AuditContext(Long userId, String ip) {

        /** 写入一条审计日志；任何异常都只告警，绝不影响主流程。 */
        public void write(AuditLogMapper mapper, String action, String resourceType,
                          Long resourceId, String detail) {
            try {
                AuditLog entity = new AuditLog();
                entity.setUserId(userId);
                entity.setAction(action);
                entity.setResourceType(resourceType == null ? "" : resourceType);
                entity.setResourceId(resourceId);
                entity.setDetail(detail == null ? "" : detail);
                entity.setIp(ip);
                mapper.insert(entity);
            } catch (Exception e) {
                log.warn("写审计日志失败 action={}", action, e);
            }
        }
    }
}
