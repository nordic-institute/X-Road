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
package org.niis.xroad.signer.core.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.mapper.GenericUniDirectionalMapper;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.core.model.RuntimeCert;
import org.niis.xroad.signer.core.model.RuntimeKey;
import org.niis.xroad.signer.protocol.dto.CertRequestInfoProto;
import org.niis.xroad.signer.protocol.dto.CertificateInfoProto;
import org.niis.xroad.signer.protocol.dto.KeyInfoProto;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor
public class KeyInfoProtoMapper implements GenericUniDirectionalMapper<RuntimeKey, KeyInfoProto> {
    private final CertificateInfoProtoMapper certificateInfoProtoMapper;
    private final CertRequestInfoProtoMapper certRequestInfoProtoMapper;

    public KeyInfo toTargetDTO(RuntimeKey source) {
        return new KeyInfo(toTarget(source));
    }

    @Override
    public KeyInfoProto toTarget(RuntimeKey source) {
        var builder = KeyInfoProto.newBuilder()
                .setId(source.externalId())
                .setAvailable(source.isAvailable())
                .addAllCerts(getCertsAsDTOs(source.certs()))
                .addAllCertRequests(getCertRequestsAsDTOs(source.certRequests()))
                .setSignMechanismName(source.signMechanismName().name());

        if (source.usage() != null) {
            builder.setUsage(source.usage());
        }

        if (source.friendlyName() != null) {
            builder.setFriendlyName(source.friendlyName());
        }

        if (source.label() != null) {
            builder.setLabel(source.label());
        }

        if (source.publicKey() != null) {
            builder.setPublicKey(source.publicKey());
        }

        return builder.build();
    }

    private List<CertificateInfoProto> getCertsAsDTOs(Collection<RuntimeCert> certs) {
        return certs.stream()
                .map(certificateInfoProtoMapper::toTarget)
                .toList();
    }

    private List<CertRequestInfoProto> getCertRequestsAsDTOs(Collection<CertRequestData> certRequests) {
        return certRequests.stream()
                .map(certRequestInfoProtoMapper::toTarget)
                .toList();
    }

}
