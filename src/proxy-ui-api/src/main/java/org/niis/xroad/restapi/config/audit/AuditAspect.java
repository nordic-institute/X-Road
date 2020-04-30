/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.config.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class AuditAspect {

    @Autowired
    public AuditAspect() {
    }

    @Before(value = "execution(* org.niis.xroad.restapi.service.ClientService.*(..)))")
    public void beforeAdvice(JoinPoint joinPoint) {
        logit("beforeAdvice");
        log.info("beforeAdvice");
    }

    @Before(value = "execution(* org.niis.xroad.restapi.openapi.ClientsApiController.*(..)))")
    public void beforeAdvice2(JoinPoint joinPoint) {
        logit("beforeAdvice2");
        log.info("beforeAdvice2");
    }

    @Before(value = "execution(* org.niis.xroad.restapi.openapi.ClientsApi.*(..)))")
    public void beforeAdvice5(JoinPoint joinPoint) {
        logit("beforeAdvice2");
        log.info("beforeAdvice2");
    }

    @AfterThrowing(value = "execution(* org.niis.xroad.restapi.openapi.ClientsApi.*(..)))")
    public void afterThrowingClientsApi(JoinPoint joinPoint) {
        logit("afterThrowingClientsApi");
        log.info("afterThrowingClientsApi");
    }

    @AfterThrowing(value = "execution(* org.niis.xroad.restapi.openapi.ClientsApiController.*(..)))")
    public void afterThrowingClientsApiController(JoinPoint joinPoint) {
        logit("afterThrowingClientsApiController");
        log.info("afterThrowingClientsApiController");
    }

    @After(value = "execution(* org.niis.xroad.restapi.openapi.ClientsApiController.*(..)))")
    public void afterAdvice(JoinPoint joinPoint) {
        logit("afterAdvice");
        log.info("afterAdvice");
    }


    @Before(value = "within(org.niis.xroad.restapi.openapi.*)")
    public void beforeAdvice3(JoinPoint joinPoint) {
        logit("beforeAdvice3");
        log.info("beforeAdvice3");
    }

    @AfterReturning(pointcut = "within(org.niis.xroad.restapi.openapi.*)")
    public void afterReturning(JoinPoint joinPoint) {
        logit("afterReturning");
        log.info("afterReturning");
    }

    @AfterThrowing(pointcut = "within(org.niis.xroad.restapi.openapi.*)")
    public void afterThrowing(JoinPoint joinPoint) {
        logit("afterThrowing");
        log.info("afterThrowing2");
    }

    private void logit(String s) {
        if (true) return;
        for (int i = 0; i < 20; i++) {
            log.info("=====================================" + s);
        }
    }

//    @AfterReturning(pointcut = "execution(@com.company.MyAnnotation * *(..)) && @annotation(myAnnotation) && args(request,..)", returning = "result")
//    public void afterReturning(JoinPoint joinPoint, Object result, MyAnnotation myAnnotation, HttpServletRequest request) {
//        // do logging
//    }
//
//    @AfterThrowing(pointcut = "execution(@com.company.MyAnnotation * *(..)) && @annotation(myAnnotation) && args(request,..)", throwing = "exception")
//    public void afterThrowing(JoinPoint joinPoint, Throwable exception, MyAnnotation myAnnotation, HttpServletRequest request) {
//        // do logging
//    }
//
}
