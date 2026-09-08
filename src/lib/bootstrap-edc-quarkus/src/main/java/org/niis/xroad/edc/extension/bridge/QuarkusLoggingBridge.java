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
package org.niis.xroad.edc.extension.bridge;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Bridge implementation for EDC Monitor that delegates to SLF4J logging.
 */
@ApplicationScoped
@Slf4j(topic = "EDC-MONITOR")
public class QuarkusLoggingBridge implements Monitor {

    @Override
    public void severe(final Supplier<String> supplier, final Throwable... errors) {
        if (log.isErrorEnabled()) {
            String message = supplier.get();
            if (errors == null || errors.length == 0) {
                log.error(message);
            } else {
                for (Throwable error : errors) {
                    log.error(message, error);
                }
            }
        }
    }

    @Override
    public void severe(final Map<String, Object> data) {
        data.forEach((key, value) -> log.error("{}: {}", key, value));
    }

    @Override
    public void warning(final Supplier<String> supplier, final Throwable... errors) {
        if (log.isWarnEnabled()) {
            String message = supplier.get();
            if (errors == null || errors.length == 0) {
                log.warn(message);
            } else {
                for (Throwable error : errors) {
                    log.warn(message, error);
                }
            }
        }
    }

    @Override
    public void info(final Supplier<String> supplier, final Throwable... errors) {
        if (log.isInfoEnabled()) {
            String message = supplier.get();
            if (errors == null || errors.length == 0) {
                log.info(message);
            } else {
                for (Throwable error : errors) {
                    log.info(message, error);
                }
            }
        }
    }

    @Override
    public void debug(final Supplier<String> supplier, final Throwable... errors) {
        if (log.isDebugEnabled()) {
            String message = supplier.get();
            if (errors == null || errors.length == 0) {
                log.debug(message);
            } else {
                for (Throwable error : errors) {
                    log.debug(message, error);
                }
            }
        }
    }
}
