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
package ee.ria.xroad.monitor.configuration;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConfBeanConfig;
import ee.ria.xroad.common.conf.globalconf.GlobalConfRefreshJobConfig;
import ee.ria.xroad.common.conf.serverconf.ServerConfBeanConfig;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.monitor.CertificateInfoSensor;
import ee.ria.xroad.monitor.DiskSpaceSensor;
import ee.ria.xroad.monitor.ExecListingSensor;
import ee.ria.xroad.monitor.MetricsRpcService;
import ee.ria.xroad.monitor.SystemMetricsSensor;

import io.grpc.BindableService;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;

@Slf4j
@Import({GlobalConfBeanConfig.class,
        ServerConfBeanConfig.class,
        GlobalConfRefreshJobConfig.class})
@EnableScheduling
@Configuration
public class MonitorConfig {
    private static final int TASK_EXECUTOR_POOL_SIZE = 5;

    @Bean(initMethod = "start", destroyMethod = "stop")
    RpcServer rpcServer(final List<BindableService> bindableServices) throws Exception {
        return RpcServer.newServer(
                SystemProperties.getGrpcInternalHost(),
                SystemProperties.getEnvMonitorPort(),
                builder -> bindableServices.forEach(bindableService -> {
                    log.info("Registering {} RPC service.", bindableService.getClass().getSimpleName());
                    builder.addService(bindableService);
                }));
    }

    @Bean
    TaskScheduler taskScheduler() {
        var taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(TASK_EXECUTOR_POOL_SIZE);
        return taskScheduler;
    }

    @Bean
    MetricsRpcService metricsRpcService() {
        return new MetricsRpcService();
    }

    @Bean
    SystemMetricsSensor systemMetricsSensor(TaskScheduler taskScheduler) throws Exception {
        return new SystemMetricsSensor(taskScheduler);
    }

    @Bean
    DiskSpaceSensor diskSpaceSensor(TaskScheduler taskScheduler) {
        return new DiskSpaceSensor(taskScheduler);
    }

    @Bean
    ExecListingSensor execListingSensor(TaskScheduler taskScheduler) {
        return new ExecListingSensor(taskScheduler);
    }

    @Bean
    CertificateInfoSensor certificateInfoSensor(TaskScheduler taskScheduler, ServerConfProvider serverConfProvider) {
        return new CertificateInfoSensor(taskScheduler, serverConfProvider);
    }
}
