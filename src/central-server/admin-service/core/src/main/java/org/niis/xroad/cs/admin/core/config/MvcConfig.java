/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    private static final String RESOURCE_ROOT = "classpath:/public/";
    private static final long CACHE_MAX_AGE = 365;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    private void addResourceLocationMapping(ResourceHandlerRegistry registry, String pathPattern,
                                            String resourceLocation, boolean enableCaching) {
        if (enableCaching) {
            registry.addResourceHandler(pathPattern).addResourceLocations(resourceLocation)
                    .setCacheControl(CacheControl.maxAge(CACHE_MAX_AGE, TimeUnit.DAYS));
        } else {
            registry.addResourceHandler(pathPattern).addResourceLocations(resourceLocation);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        addResourceLocationMapping(registry, "/index.html", RESOURCE_ROOT, false);
        addResourceLocationMapping(registry, "/favicon.ico", RESOURCE_ROOT, true);
        addResourceLocationMapping(registry, "/css/**", RESOURCE_ROOT + "css/", true);
        addResourceLocationMapping(registry, "/img/**", RESOURCE_ROOT + "img/", true);
        addResourceLocationMapping(registry, "/js/**", RESOURCE_ROOT + "js/", true);
        addResourceLocationMapping(registry, "/fonts/**", RESOURCE_ROOT + "fonts/", true);
    }
}
