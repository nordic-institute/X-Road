package org.niis.xroad.proxy.core.configuration;

import org.niis.xroad.opmonitor.api.AbstractOpMonitoringBuffer;
import org.niis.xroad.proxy.core.opmonitoring.NullOpMonitoringBuffer;
import org.niis.xroad.proxy.core.opmonitoring.OpMonitoring;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxyOpMonitoringConfig {

    @Bean
    AbstractOpMonitoringBuffer opMonitoringBuffer(ServerConfProvider serverConfProvider) throws Exception {
        return OpMonitoring.init(serverConfProvider);
    }

    @Bean("opMonitoringEnabledStatus")
    Boolean messageLogEnabledStatus(AbstractOpMonitoringBuffer opMonitoringBuffer) {
        return NullOpMonitoringBuffer.class != opMonitoringBuffer.getClass();
    }
}
