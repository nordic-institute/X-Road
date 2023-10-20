/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.signer;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.signer.certmanager.OcspClientWorker;
import ee.ria.xroad.signer.certmanager.OcspResponseManager;
import ee.ria.xroad.signer.job.OcspClientExecuteScheduler;
import ee.ria.xroad.signer.tokenmanager.module.AbstractModuleManager;
import ee.ria.xroad.signer.tokenmanager.module.DefaultModuleManagerImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
@EnableScheduling
@Import({SignerAdminPortConfig.class, SignerRpcConfig.class})
@ComponentScan({"ee.ria.xroad.signer.protocol", "ee.ria.xroad.signer.job"})
@Configuration
public class SignerConfig {
    private static final String MODULE_MANAGER_IMPL_CLASS = SystemProperties.PREFIX + "signer.moduleManagerImpl";
    static final int OCSP_SCHEDULER_BEAN_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    @Bean("moduleManager")
    AbstractModuleManager moduleManager() {
        final String moduleManagerImplClassName = System.getProperty(MODULE_MANAGER_IMPL_CLASS, DefaultModuleManagerImpl.class.getName());
        log.debug("Using module manager implementation: {}", moduleManagerImplClassName);

        try {
            var clazz = Class.forName(moduleManagerImplClassName);
            return (AbstractModuleManager) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not load module manager impl: " + moduleManagerImplClassName, e);
        }
    }

    @Bean
    OcspResponseManager ocspResponseManager() {
        OcspResponseManager ocspResponseManager = new OcspResponseManager();
        ocspResponseManager.init();
        return ocspResponseManager;
    }

    @Bean
    OcspClientWorker ocspClientWorker(OcspResponseManager ocspResponseManager) {
        return new OcspClientWorker(ocspResponseManager);
    }

    @Bean
    TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Order(OCSP_SCHEDULER_BEAN_ORDER)
    @Bean(name = "ocspClientExecuteScheduler")
    @Conditional(IsOcspClientJobsActive.class)
    OcspClientExecuteScheduler ocspClientExecuteScheduler(OcspClientWorker ocspClientWorker, TaskScheduler taskScheduler) {
        OcspClientExecuteScheduler scheduler = new OcspClientExecuteScheduler(ocspClientWorker, taskScheduler);
        scheduler.init();
        return scheduler;
    }

    @Slf4j
    public static class IsOcspClientJobsActive implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            boolean isActive = SystemProperties.isOcspResponseRetrievalActive();
            if (!isActive) {
                log.info("OCSP-retrieval configured to be inactive, job auto-scheduling disabled");
            }
            return isActive;
        }
    }

}
