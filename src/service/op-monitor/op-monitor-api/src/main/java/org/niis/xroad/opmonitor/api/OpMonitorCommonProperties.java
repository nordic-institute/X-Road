package org.niis.xroad.opmonitor.api;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "xroad.op-monitor")
public interface OpMonitorCommonProperties {

    /**
     * Listen address of operational monitoring daemon.
     *
     * @return host address
     */
    @WithName("host")
    @WithDefault("localhost")
    String host();

    /**
     * Listen port of operational monitoring daemon.
     *
     * @return port number
     */
    @WithName("port")
    @WithDefault("2080")
    int port();
    /**
     * URI scheme name determining the used connection type.
     *
     * @return http or https
     */
    @WithName("scheme")
    @WithDefault("http")
    String scheme();


    /**
     * Property name of the path to the location of the TLS certificate used by the HTTP client sending requests to the
     * operational data daemon. Validated by the daemon server and should be the security server internal certificate.
     */
    @WithName("client-tls-certificate")
    @WithDefault("/etc/xroad/ssl/internal.crt")
    String clientTlsCertificate();
}
