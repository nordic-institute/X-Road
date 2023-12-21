/*
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
package org.niis.xroad.cs.test.container.database;

import liquibase.logging.core.AbstractLogger;
import org.slf4j.Logger;

import java.util.logging.Level;

final class LiquibaseSlf4jLogger extends AbstractLogger {
    private final Logger logger;

    LiquibaseSlf4jLogger(Logger logger) {
        super();
        this.logger = logger;
    }

    @Override
    public void log(Level level, String message, Throwable e) {
        int levelValue = level.intValue();
        if (levelValue <= Level.FINEST.intValue()) {
            logger.trace(message, e);
        } else if (levelValue <= Level.FINE.intValue()) {
            logger.debug(message, e);
        } else if (levelValue <= Level.INFO.intValue()) {
            logger.info(message, e);
        } else if (levelValue <= Level.WARNING.intValue()) {
            logger.warn(message, e);
        } else {
            logger.error(message, e);
        }
    }

    @Override
    public void severe(String message) {
        if (logger.isErrorEnabled()) {
            logger.error(message);
        }
    }

    @Override
    public void severe(String message, Throwable e) {
        if (logger.isErrorEnabled()) {
            logger.error(message, e);
        }
    }

    @Override
    public void warning(String message) {
        if (logger.isWarnEnabled()) {
            logger.warn(message);
        }
    }

    @Override
    public void warning(String message, Throwable e) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, e);
        }
    }

    @Override
    public void info(String message) {
        if (logger.isInfoEnabled()) {
            logger.info(message);
        }
    }

    @Override
    public void info(String message, Throwable e) {
        if (logger.isInfoEnabled()) {
            logger.info(message, e);
        }
    }

    @Override
    public void config(String message) {
        if (logger.isInfoEnabled()) {
            logger.info(message);
        }
    }

    @Override
    public void config(String message, Throwable e) {
        if (logger.isInfoEnabled()) {
            logger.info(message, e);
        }
    }

    @Override
    public void fine(String message) {
        if (logger.isDebugEnabled()) {
            logger.debug(message);
        }
    }

    @Override
    public void fine(String message, Throwable e) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, e);
        }
    }
}
