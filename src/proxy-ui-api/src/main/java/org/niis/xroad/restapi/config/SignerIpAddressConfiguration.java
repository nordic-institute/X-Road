package org.niis.xroad.restapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Enable customization of signer IP address when development profile is active.
 * Otherwise use 127.0.0.1
 */
@Configuration
public class SignerIpAddressConfiguration {

    @Value("${custom.ip:127.0.0.1}")
    private String customIp;

    @Bean(name = "signer-ip")
    @Profile("!development")
    public String defaultBean() {
        return "127.0.0.1";
    }

    @Bean(name = "signer-ip")
    @Profile("development")
    public String customBean() {
        return customIp;
    }

}
