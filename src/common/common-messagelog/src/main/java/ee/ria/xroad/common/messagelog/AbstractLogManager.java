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
package ee.ria.xroad.common.messagelog;

import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.util.JobManager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for log manager actors.
 */
@Slf4j
public abstract class AbstractLogManager implements DisposableBean {
    protected static Map<String, DiagnosticsStatus> statusMap = new ConcurrentHashMap<>();

    protected AbstractLogManager(JobManager jobManager, GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider) {
        if (globalConfProvider == null) {
            throw new IllegalArgumentException("globalConfProvider cannot be null");
        }
        if (jobManager == null) {
            throw new IllegalArgumentException("jobManager cannot be null");
        }
        if (serverConfProvider == null) {
            throw new IllegalArgumentException("serverConfProvider cannot be null");
        }
    }

    public abstract void log(LogMessage message) throws Exception;

    public abstract TimestampRecord timestamp(Long messageRecordId) throws Exception;

    public abstract Map<String, DiagnosticsStatus> getDiagnosticStatus();

    @Override
    public void destroy() {
        // NO-OP
    }

}
