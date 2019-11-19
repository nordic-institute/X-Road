package org.niis.xroad.restapi.devtools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.PropertySource;

/**
 * devtools -enabled main spring boot application.
 */
@ServletComponentScan(basePackages = {"org.niis.xroad.restapi"})
@SpringBootApplication(scanBasePackages = {"org.niis.xroad.restapi"})
@PropertySource("classpath:/common-application.properties")
@EnableCaching
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class DevtoolsRestApiApplication {
    /**
     * start application
     */
    public static void main(String[] args) {
        SpringApplication.run(DevtoolsRestApiApplication.class, args);
    }
}
