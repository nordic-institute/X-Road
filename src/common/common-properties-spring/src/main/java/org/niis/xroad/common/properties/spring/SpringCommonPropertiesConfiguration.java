package org.niis.xroad.common.properties.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@EnableConfigurationProperties({
//        SpringCommonGlobalConfProperties.class,
//        SpringCommonRpcProperties.class
//})
public class SpringCommonPropertiesConfiguration {

    @Bean
    SpringCommonGlobalConfProperties springCommonGlobalConfProperties() {
        //TODO: hardcoded as spring boot is not present in most modules
        return new SpringCommonGlobalConfProperties();
    }

    @Bean
    SpringCommonRpcProperties springCommonRpcProperties() {
        //TODO: hardcoded as spring boot is not present in most modules
        return new SpringCommonRpcProperties(false, null);
    }

}
