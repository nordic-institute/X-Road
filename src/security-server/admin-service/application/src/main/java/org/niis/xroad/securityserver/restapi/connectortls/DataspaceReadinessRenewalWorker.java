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
package org.niis.xroad.securityserver.restapi.connectortls;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.spring.scheduling.AcmeRenewalWorker;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;

/**
 * Runs the wrapped renewal worker only while the instance is dataspace ready. A tick while not ready ends through
 * {@link CertificateRenewalScheduler#globalConfInvalidated()}, so the next readiness check follows after the
 * scheduler's short recovery delay rather than the full renewal interval, and is not counted as a failure.
 */
@Slf4j
@RequiredArgsConstructor
class DataspaceReadinessRenewalWorker implements AcmeRenewalWorker {

    private final DataspaceReadinessPredicates readinessPredicates;
    private final AcmeRenewalWorker delegate;

    @Override
    public void execute(CertificateRenewalScheduler scheduler) {
        if (!readinessPredicates.isDataspaceReady()) {
            log.debug("Data space is not ready, DS TLS ACME renewal skipped");
            if (scheduler != null) {
                scheduler.globalConfInvalidated();
            }
            return;
        }
        delegate.execute(scheduler);
    }
}
