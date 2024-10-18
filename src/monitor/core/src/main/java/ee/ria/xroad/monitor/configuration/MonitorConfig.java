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

import ee.ria.xroad.common.conf.globalconf.GlobalConfBeanConfig;
import ee.ria.xroad.common.conf.globalconf.GlobalConfRefreshJobConfig;
import ee.ria.xroad.common.conf.serverconf.ServerConfBeanConfig;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.monitor.CertificateInfoSensor;
import ee.ria.xroad.monitor.DiskSpaceSensor;
import ee.ria.xroad.monitor.EnvMonitorProperties;
import ee.ria.xroad.monitor.ExecListingSensor;
import ee.ria.xroad.monitor.MetricsRpcService;
import ee.ria.xroad.monitor.SystemMetricsSensor;
import ee.ria.xroad.signer.protocol.RpcSignerClient;

import io.grpc.BindableService;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcClientProperties;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.confclient.proto.ConfClientRpcClient;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Bean
    RpcServer rpcServer(final List<BindableService> bindableServices, RpcServerProperties envMonitorRpcServerProperties) throws Exception {
        return RpcServer.newServer(
                envMonitorRpcServerProperties,
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
    MetricsRpcService metricsRpcService(EnvMonitorProperties envMonitorProperties) {
        return new MetricsRpcService(envMonitorProperties);
    }

    @Bean
    SystemMetricsSensor systemMetricsSensor(TaskScheduler taskScheduler,
                                            EnvMonitorProperties envMonitorProperties,
                                            @Qualifier("proxyRpcClientProperties") RpcClientProperties proxyRpcClientProperties)
            throws Exception {
        return new SystemMetricsSensor(taskScheduler, envMonitorProperties, proxyRpcClientProperties);
    }

    @Bean
    DiskSpaceSensor diskSpaceSensor(TaskScheduler taskScheduler, EnvMonitorProperties envMonitorProperties) {
        return new DiskSpaceSensor(taskScheduler, envMonitorProperties);
    }

    @Bean
    ExecListingSensor execListingSensor(TaskScheduler taskScheduler, EnvMonitorProperties envMonitorProperties) {
        return new ExecListingSensor(taskScheduler, envMonitorProperties);
    }

    @Bean
    CertificateInfoSensor certificateInfoSensor(TaskScheduler taskScheduler, EnvMonitorProperties envMonitorProperties,
                                                ServerConfProvider serverConfProvider) {
        return new CertificateInfoSensor(taskScheduler, envMonitorProperties, serverConfProvider);
    }

    @Bean
    ConfClientRpcClient confClientRpcClient(@Qualifier("confClientRpcClientProperties") RpcClientProperties confClientRpcClientProperties) {
        return new ConfClientRpcClient(confClientRpcClientProperties);
    }

    @Bean
    RpcSignerClient rpcSignerClient(@Qualifier("signerRpcClientProperties") RpcClientProperties signerRpcClientProperties)
            throws Exception {
        return RpcSignerClient.init(signerRpcClientProperties);
    }

}
