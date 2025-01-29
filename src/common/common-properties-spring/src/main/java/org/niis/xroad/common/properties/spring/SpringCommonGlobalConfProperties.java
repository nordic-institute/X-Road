package org.niis.xroad.common.properties.spring;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.niis.xroad.common.properties.CommonGlobalConfProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "xroad.common.global-conf")
public class SpringCommonGlobalConfProperties implements CommonGlobalConfProperties {
    private GlobalConfSource source = GlobalConfSource.FILESYSTEM;

    @Override
    public GlobalConfSource source() {
        return source;
    }

}

