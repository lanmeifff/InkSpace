package com.inkspace.common.audit;

import com.inkspace.common.audit.AuditRecorder.AuditContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 审计切面：拦截 @OperationLog，把「谁在什么时间对哪个资源做了什么」写入 audit_log；
 * 写日志失败只告警，不影响主流程。
 * 用户名/IP 需在 proceed() 前于请求线程取快照：流式接口返回后响应已提交，异步逻辑在其它线程执行。
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final AuditRecorder auditRecorder;

    public OperationLogAspect(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        AuditContext context = auditRecorder.capture();
        String error = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            error = throwable.getClass().getSimpleName();
            throw throwable;
        } finally {
            write(joinPoint, operationLog, context, error);
        }
    }

    private void write(ProceedingJoinPoint joinPoint, OperationLog operationLog,
                       AuditContext context, String error) {
        try {
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            MethodBasedEvaluationContext evaluationContext = new MethodBasedEvaluationContext(
                    null, method, joinPoint.getArgs(), new DefaultParameterNameDiscoverer());

            Long resourceId = evaluate(operationLog.resourceId(), evaluationContext, Long.class);
            String detail = evaluate(operationLog.detail(), evaluationContext, String.class);
            if (error != null) {
                detail = (detail == null ? "" : detail) + " error=" + error;
            }
            context.write(auditRecorder.mapper(), operationLog.action(), operationLog.resourceType(),
                    resourceId, detail);
        } catch (Exception e) {
            log.warn("写审计日志失败 action={}", operationLog.action(), e);
        }
    }

    private <T> T evaluate(String expression, MethodBasedEvaluationContext context, Class<T> type) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        try {
            return PARSER.parseExpression(expression).getValue(context, type);
        } catch (Exception e) {
            log.debug("审计表达式求值失败 expr={}", expression);
            return null;
        }
    }
}
