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
package org.niis.xroad.signer.softtoken.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.softtoken.config.SoftTokenSignerKeysProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeysSynchronizationJobTest {

    @Mock
    private SignerRpcClient signerRpcClient;
    @Mock
    private SoftwareTokenKeyCache keyCache;
    @Mock
    private SoftTokenSignerKeysProperties properties;

    private SyncHealthState syncHealthState;
    private KeysSynchronizationJob job;

    @BeforeEach
    void setUp() {
        syncHealthState = new SyncHealthState();
        job = new KeysSynchronizationJob(signerRpcClient, keyCache, properties, syncHealthState);
    }

    @Test
    void synchronizeKeysSuccessRecordsSuccessInHealthState() throws Exception {
        when(signerRpcClient.listSoftwareTokenKeys()).thenReturn(List.of());

        job.synchronizeKeys();

        assertThat(syncHealthState.getConsecutiveFailures()).isZero();
        assertThat(syncHealthState.getLastSuccessfulSync()).isPresent();
    }

    @Test
    void synchronizeKeysInitialFailureThrowsXrdRuntimeException() throws Exception {
        when(signerRpcClient.listSoftwareTokenKeys()).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> job.synchronizeKeys())
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void synchronizeKeysPeriodicFailureCatchesAndRecordsFailure() throws Exception {
        setInitialSyncDone(true);
        when(signerRpcClient.listSoftwareTokenKeys()).thenThrow(new RuntimeException("connection refused"));

        job.synchronizeKeys();

        assertThat(syncHealthState.getConsecutiveFailures()).isEqualTo(1);
        assertThat(syncHealthState.getLastFailureMessage()).contains("connection refused");
    }

    @Test
    void synchronizeKeysPeriodicFailureDoesNotRethrow() throws Exception {
        setInitialSyncDone(true);
        when(signerRpcClient.listSoftwareTokenKeys()).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> job.synchronizeKeys()).doesNotThrowAnyException();
    }

    @Test
    void synchronizeKeysRecoveryAfterPeriodicFailureResetsCount() throws Exception {
        setInitialSyncDone(true);

        // First call fails, second call succeeds
        doThrow(new RuntimeException("connection refused"))
                .doReturn(List.of())
                .when(signerRpcClient).listSoftwareTokenKeys();

        job.synchronizeKeys();
        assertThat(syncHealthState.getConsecutiveFailures()).isEqualTo(1);

        job.synchronizeKeys();
        assertThat(syncHealthState.getConsecutiveFailures()).isZero();
    }

    private void setInitialSyncDone(boolean value) {
        try {
            var field = KeysSynchronizationJob.class.getDeclaredField("initialSyncDone");
            field.setAccessible(true);
            field.set(job, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set initialSyncDone", e);
        }
    }
}
