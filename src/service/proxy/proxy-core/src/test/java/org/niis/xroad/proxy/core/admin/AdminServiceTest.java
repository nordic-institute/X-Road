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

package org.niis.xroad.proxy.core.admin;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyInfo;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenSigningKey;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.configuration.AgreementTokenKeyMaterial;
import org.niis.xroad.proxy.proto.AgreementTokenKeyStatusResp;
import org.niis.xroad.proxy.proto.RotateAgreementTokenSigningKeyResp;
import org.niis.xroad.rpc.common.Empty;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private final AgreementTokenKeyMaterial keyMaterial = mock(AgreementTokenKeyMaterial.class);
    private final AdminService adminService = new AdminService(null, null, null, null, null, null, null, keyMaterial);

    @Test
    void shouldReportUnavailableWithoutErrorWhenNoKeyMaterialWasEverLoaded() {
        when(keyMaterial.provider()).thenReturn(Optional.empty());

        var status = keyStatus();

        assertThat(status.getAvailable()).isFalse();
        assertThat(status.hasActiveKeyId()).isFalse();
        assertThat(status.hasActiveKeyCreatedAt()).isFalse();
        assertThat(status.getRetainedKeyCount()).isZero();
        assertThat(status.hasPendingKeyId()).isFalse();
    }

    @Test
    void shouldReportTheSigningKeyAndNoPendingKeyWhenTheNewestKeyIsSigning() {
        givenKeys(new AgreementTokenKeyInfo("1", Optional.of(CREATED_AT.minusSeconds(60)), false),
                new AgreementTokenKeyInfo("2", Optional.of(CREATED_AT), true));

        var status = keyStatus();

        assertThat(status.getAvailable()).isTrue();
        assertThat(status.getActiveKeyId()).isEqualTo("2");
        assertThat(status.getActiveKeyCreatedAt()).isEqualTo(CREATED_AT.toEpochMilli());
        assertThat(status.getRetainedKeyCount()).isEqualTo(2);
        assertThat(status.hasPendingKeyId()).isFalse();
    }

    @Test
    void shouldReportTheNewestKeyAsPendingWhileItIsNotSigningYet() {
        givenKeys(new AgreementTokenKeyInfo("1", Optional.of(CREATED_AT), true),
                new AgreementTokenKeyInfo("2", Optional.of(CREATED_AT.plusSeconds(5)), false));

        var status = keyStatus();

        assertThat(status.getActiveKeyId()).isEqualTo("1");
        assertThat(status.getRetainedKeyCount()).isEqualTo(2);
        assertThat(status.getPendingKeyId()).isEqualTo("2");
    }

    @Test
    void shouldLeaveCreationTimeUnsetForAKeyWithoutIssueTime() {
        givenKeys(new AgreementTokenKeyInfo("1", Optional.empty(), true));

        var status = keyStatus();

        assertThat(status.getActiveKeyId()).isEqualTo("1");
        assertThat(status.hasActiveKeyCreatedAt()).isFalse();
    }

    @Test
    void shouldFailRotateWithKeyNotAvailableWhenNoKeyMaterialWasEverLoaded() {
        when(keyMaterial.provider()).thenReturn(Optional.empty());
        var observer = new RecordingObserver<RotateAgreementTokenSigningKeyResp>();

        adminService.rotateAgreementTokenSigningKey(Empty.getDefaultInstance(), observer);

        assertThat(observer.error).isInstanceOfSatisfying(XrdRuntimeException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AGREEMENT_TOKEN_KEY_NOT_AVAILABLE.code()));
    }

    private void givenKeys(AgreementTokenKeyInfo... keys) {
        when(keyMaterial.provider()).thenReturn(Optional.of(new ListingKeyProvider(List.of(keys))));
    }

    private AgreementTokenKeyStatusResp keyStatus() {
        var observer = new RecordingObserver<AgreementTokenKeyStatusResp>();
        adminService.getAgreementTokenKeyStatus(Empty.getDefaultInstance(), observer);
        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        return observer.value;
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T next) {
            value = next;
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }

    private record ListingKeyProvider(List<AgreementTokenKeyInfo> keys) implements AgreementTokenKeyProvider {

        @Override
        public List<AgreementTokenKeyInfo> listKeys() {
            return keys;
        }

        @Override
        public AgreementTokenSigningKey activeKey() {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public Optional<AgreementTokenSigningKey> keyById(String keyId) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public AgreementTokenSigningKey rotate() {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public void refresh() {
            throw new UnsupportedOperationException("not needed by this test");
        }
    }
}
