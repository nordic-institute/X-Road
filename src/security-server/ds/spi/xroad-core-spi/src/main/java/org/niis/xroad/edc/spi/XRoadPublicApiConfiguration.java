package org.niis.xroad.edc.spi;

import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;

@Settings
public record XRoadPublicApiConfiguration(
        @Setting(key = "web.http." + XROAD_PUBLIC_API_CONTEXT + ".port", description = "Port for " + XROAD_PUBLIC_API_CONTEXT + " api context", defaultValue = XROAD_PUBLIC_API_DEFAULT_PORT + "")
        int port,
        @Setting(key = "web.http." + XROAD_PUBLIC_API_CONTEXT + ".path", description = "Path for " + XROAD_PUBLIC_API_CONTEXT + " api context", defaultValue = XROAD_PUBLIC_API_DEFAULT_PATH)
        String path,
        @Setting(key = "web.http." + XROAD_PUBLIC_API_CONTEXT + ".needClientAuth", description = "mTLS conf for " + XROAD_PUBLIC_API_CONTEXT + " api context", defaultValue = "false")
        boolean needClientAuth
) {
    public static final String XROAD_PUBLIC_API_CONTEXT = "xroad.public";
    public static final int XROAD_PUBLIC_API_DEFAULT_PORT = 9294;
    public static final String XROAD_PUBLIC_API_DEFAULT_PATH = "/xroad/public";
}
