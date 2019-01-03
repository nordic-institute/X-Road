package org.niis.xroad.authproto;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration for some test / development pages
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    /**
     * Configuration for spring mvc view controllers
     * @param registry
     */
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/home").setViewName("home");
        registry.addViewController("/").setViewName("home");
        registry.addViewController("/hello").setViewName("hello");
        registry.addViewController("/login").setViewName("login");
        registry.addViewController("/admin/hello").setViewName("admin/hello");
        registry.addViewController("/db/hello").setViewName("db/hello");
        registry.addViewController("/standard/hello").setViewName("standard/hello");
    }
}
