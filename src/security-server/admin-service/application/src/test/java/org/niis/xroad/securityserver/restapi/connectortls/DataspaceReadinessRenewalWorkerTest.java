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
package org.niis.xroad.securityserver.restapi.connectortls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.acme.spring.scheduling.AcmeRenewalWorker;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceReadinessRenewalWorkerTest {

    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
    @Mock
    private AcmeRenewalWorker delegate;
    @Mock
    private CertificateRenewalScheduler scheduler;

    private DataspaceReadinessRenewalWorker worker;

    @BeforeEach
    void setUp() {
        worker = new DataspaceReadinessRenewalWorker(readinessPredicates, delegate);
    }

    @Test
    void notReadyTickSkipsWorkerAndSchedulesRecheck() {
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);

        worker.execute(scheduler);

        verifyNoInteractions(delegate);
        verify(scheduler).deferCycle();
        verifyNoMoreInteractions(scheduler);
    }

    @Test
    void readyTickDelegatesWithSameScheduler() {
        when(readinessPredicates.isDataspaceReady()).thenReturn(true);

        worker.execute(scheduler);

        verify(delegate).execute(scheduler);
        verifyNoInteractions(scheduler);
    }

    @Test
    void renewsOnNextTickOnceInstanceBecomesReady() {
        when(readinessPredicates.isDataspaceReady()).thenReturn(false, true);

        worker.execute(scheduler);
        verify(delegate, never()).execute(scheduler);

        worker.execute(scheduler);
        verify(delegate).execute(scheduler);
    }
}
