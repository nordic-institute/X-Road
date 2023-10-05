/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.restapi.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Set;

@Slf4j
public abstract class AbstractDeprecatedPropsChecker implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    private void check() {
        final var env = applicationContext.getEnvironment();
        getDeprecatedProperties().stream()
                .filter(prop -> env.containsProperty(prop.key))
                .forEach(this::logWarning);
    }

    private void logWarning(final DeprecatedProperty prop) {
        final var msgBuilder = new StringBuilder("Configuration property: ")
                .append(prop.key)
                .append(" is deprecated and may not work as intended.");
        if (StringUtils.isNotEmpty(prop.replacementKey)) {
            msgBuilder
                    .append(" Replace it with new property: ")
                    .append(prop.replacementKey);
        }
        if (StringUtils.isNotEmpty(prop.comment)) {
            msgBuilder
                    .append(". ")
                    .append(prop.comment);
        }

        log.warn(msgBuilder.toString());
    }

    protected abstract Set<DeprecatedProperty> getDeprecatedProperties();

    @Getter
    @RequiredArgsConstructor
    public static class DeprecatedProperty {
        private final String key;
        private final String replacementKey;
        private final String comment;

        public DeprecatedProperty(final String key, final String replacementKey) {
            this(key, replacementKey, null);
        }
    }
}
