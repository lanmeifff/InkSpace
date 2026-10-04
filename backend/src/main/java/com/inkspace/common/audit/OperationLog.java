package com.inkspace.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要写审计日志的接口方法。
 * resourceId / detail 支持 SpEL（例如 "#id"、"#request.noteId"）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 动作名，如 NOTE_DELETE */
    String action();

    String resourceType() default "";

    String resourceId() default "";

    /** 补充信息表达式；注意不要引用密码等敏感参数 */
    String detail() default "";
}
