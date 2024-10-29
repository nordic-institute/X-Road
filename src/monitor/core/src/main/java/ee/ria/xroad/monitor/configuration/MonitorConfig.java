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
import ee.ria.xroad.signer.SignerClientConfiguration;
import ee.ria.xroad.signer.SignerRpcClient;

import io.grpc.BindableService;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcServiceProperties;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.confclient.proto.ConfClientRpcClientConfiguration;
import org.niis.xroad.proxy.proto.ProxyRpcChannelProperties;
import org.niis.xroad.proxy.proto.ProxyRpcClientConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
        GlobalConfRefreshJobConfig.class,
        SignerClientConfiguration.class,
        ConfClientRpcClientConfiguration.class,
        ProxyRpcClientConfiguration.class})
@EnableScheduling
@EnableConfigurationProperties({EnvMonitorProperties.class})
@Configuration
public class MonitorConfig {
    private static final int TASK_EXECUTOR_POOL_SIZE = 5;

    @Bean
    RpcServer rpcServer(final List<BindableService> bindableServices, RpcServiceProperties envMonitorRpcServiceProperties) throws Exception {
        return RpcServer.newServer(
                envMonitorRpcServiceProperties,
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
                                            RpcChannelFactory rpcChannelFactory,
                                            ProxyRpcChannelProperties proxyRpcClientProperties,
                                            RpcServiceProperties rpcServiceProperties) throws Exception {

        return new SystemMetricsSensor(taskScheduler, envMonitorProperties, rpcChannelFactory, proxyRpcClientProperties, rpcServiceProperties);
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
                                                ServerConfProvider serverConfProvider, SignerRpcClient signerRpcClient) {
        return new CertificateInfoSensor(taskScheduler, envMonitorProperties, serverConfProvider, signerRpcClient);
    }

}
