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
package org.niis.xroad.signer.core;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.spring.SpringRpcConfig;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.spring.SpringGlobalConfConfig;
import org.niis.xroad.signer.core.certmanager.FileBasedOcspCache;
import org.niis.xroad.signer.core.certmanager.OcspClient;
import org.niis.xroad.signer.core.certmanager.OcspClientWorker;
import org.niis.xroad.signer.core.certmanager.OcspResponseManager;
import org.niis.xroad.signer.core.job.OcspClientExecuteScheduler;
import org.niis.xroad.signer.core.tokenmanager.module.AbstractModuleManager;
import org.niis.xroad.signer.core.tokenmanager.module.DefaultModuleManagerImpl;
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
@Import({SignerAdminPortConfig.class,
        SignerRpcConfig.class,
        SpringGlobalConfConfig.class,
        SpringRpcConfig.class
})
@ComponentScan({
        "org.niis.xroad.signer.core.protocol",
        "org.niis.xroad.signer.core.job",
        "org.niis.xroad.signer.core.certmanager"})
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
    FileBasedOcspCache ocspCache(GlobalConfProvider globalConfProvider) {
        return new FileBasedOcspCache(globalConfProvider);
    }

    @Bean
    OcspResponseManager ocspResponseManager(GlobalConfProvider globalConfProvider, OcspClient ocspClient, FileBasedOcspCache ocspCache) {
        OcspResponseManager ocspResponseManager = new OcspResponseManager(globalConfProvider, ocspClient, ocspCache);
        ocspResponseManager.init();
        return ocspResponseManager;
    }

    @Bean
    OcspClientWorker ocspClientWorker(GlobalConfProvider globalConfProvider, OcspResponseManager ocspResponseManager,
                                      OcspClient ocspClient) {
        return new OcspClientWorker(globalConfProvider, ocspResponseManager, ocspClient);
    }

    @Bean
    TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Order(OCSP_SCHEDULER_BEAN_ORDER)
    @Bean(name = "ocspClientExecuteScheduler")
    @Conditional(IsOcspClientJobsActive.class)
    OcspClientExecuteScheduler ocspClientExecuteScheduler(OcspClientWorker ocspClientWorker, TaskScheduler taskScheduler,
                                                          GlobalConfProvider globalConfProvider) {
        OcspClientExecuteScheduler scheduler = new OcspClientExecuteScheduler(ocspClientWorker, taskScheduler, globalConfProvider);
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
