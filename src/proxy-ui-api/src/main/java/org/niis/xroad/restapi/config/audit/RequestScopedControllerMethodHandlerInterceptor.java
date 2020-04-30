package org.niis.xroad.restapi.config.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;

@Component
@Slf4j
public class RequestScopedControllerMethodHandlerInterceptor implements HandlerInterceptor {

    @Autowired
    @Lazy
    AuditEventHolder auditEventHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HandlerMethod method = (HandlerMethod) handler;
        Method javaMethod = method.getMethod();
        if (javaMethod.isAnnotationPresent(AuditEventMethod.class)) {
            AuditEventMethod auditEventMethod = method.getMethodAnnotation(AuditEventMethod.class);
            auditEventHolder.setEventName(auditEventMethod.eventName());
        } else {
            auditEventHolder.setEventName(null);
        }
//        auditLog("method handler preHandle");
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        auditEventHolder.auditLog("method handler postHandle");
        HandlerMethod method = (HandlerMethod) handler;
//        log.info("postHandle " + method);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        auditEventHolder.auditLog("method handler afterCompletion");
        HandlerMethod method = (HandlerMethod) handler;
//        log.info("afterCompletion " + method);
    }

//    private static final String PREFIX = "-----------------oooooooooooooo---------------- ";
//
//    private void auditLog(String s) {
//        AuditLogger.log(PREFIX + s);
//        AuditLogger.log(auditEventHolder.getEventName(), auditEventHolder.getEventData());
//    }
}
