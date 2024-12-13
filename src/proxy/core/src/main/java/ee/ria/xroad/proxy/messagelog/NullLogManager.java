/*
 * The MIT License
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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.db.DatabaseCtxV2;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.LogMessage;
import ee.ria.xroad.common.messagelog.TimestampRecord;

import java.util.Map;

/**
 * A dummy implementation of message log that does nothing.
 * Actual implementation can be provided by addon.
 */
public class NullLogManager extends AbstractLogManager {

    public NullLogManager(String origin, GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider, DatabaseCtxV2 databaseCtx) {
        super(origin, globalConfProvider, serverConfProvider, databaseCtx);
    }

    @Override
    public void log(LogMessage message) {
        // do nothing
    }

    @Override
    public TimestampRecord timestamp(Long messageRecordId) {
        return null;
    }

    @Override
    public Map<String, DiagnosticsStatus> getDiagnosticStatus() {
        throw new RuntimeException("Status not available while using NullLogManager");
    }

}
