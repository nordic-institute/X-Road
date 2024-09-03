package ee.ria.xroad.common.conf.serverconf;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServerConfBeanConfig {

    @Bean
    ServerConfProvider serverConfProvider(GlobalConfProvider globalConfProvider) {
        //TODO should this succeed if init fails?
        if (SystemProperties.getServerConfCachePeriod() > 0) {
            return new CachingServerConfImpl(globalConfProvider);
        }
        return new ServerConfImpl(globalConfProvider);

    }
}
