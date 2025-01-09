package org.niis.xroad.proxy;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "xroad.proxy.addon")
public interface ProxyAddonProperties {
    @WithName("proxy-monitor")
    ProxyAddonProxyMonitorProperties proxyMonitor();

    @WithName("meta-services")
    ProxyAddonMetaservicesProperties metaservices();

    @WithName("message-log")
    ProxyAddonMessageLogProperties messagelog();

    @WithName("op-monitor")
    ProxyAddonOpMonitorProperties opmonitor();

    interface ProxyAddonMetaservicesProperties {
        @WithName("enabled")
        boolean enabled();
    }

    interface ProxyAddonProxyMonitorProperties {
        @WithName("enabled")
        boolean enabled();
    }

    interface ProxyAddonMessageLogProperties {
        @WithName("enabled")
        boolean enabled();
    }

    interface ProxyAddonOpMonitorProperties {
        @WithName("enabled")
        boolean enabled();
    }
}
