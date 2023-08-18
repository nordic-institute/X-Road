/**
 * The MIT License
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
package ee.ria.xroad.signer.protocol.dto;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * Certificate info DTO.
 */
@RequiredArgsConstructor
@ToString(exclude = {"certificateBytes", "ocspBytes"})//TODO:grpc
public class CertificateInfo implements Serializable {

    public static final String STATUS_SAVED = "saved";
    public static final String STATUS_REGINPROG = "registration in progress";
    public static final String STATUS_REGISTERED = "registered";
    public static final String STATUS_DELINPROG = "deletion in progress";
    public static final String STATUS_GLOBALERR = "global error";

    public static final String OCSP_RESPONSE_GOOD = "good";
    public static final String OCSP_RESPONSE_REVOKED = "revoked";
    public static final String OCSP_RESPONSE_UNKNOWN = "unknown";
    public static final String OCSP_RESPONSE_SUSPENDED = "suspended";

    private final CertificateInfoProto message;

    public ClientId.Conf getMemberId() {
        ClientIdProto memberId = message.getMemberId();
        //TODO:grpc refine this check
        if (message.getMemberId().hasField(ClientIdProto.getDescriptor().findFieldByName("subsystem_code"))) {
            return ClientId.Conf.create(memberId.getXroadInstance(),
                    memberId.getMemberClass(),
                    memberId.getMemberCode(),
                    memberId.getSubsystemCode());
        } else {
            return ClientId.Conf.create(memberId.getXroadInstance(),
                    memberId.getMemberClass(),
                    memberId.getMemberCode());
        }
    }

    public boolean isActive() {
        return message.getActive();
    }

    public boolean isSavedToConfiguration() {
        return message.getSavedToConfiguration();
    }

    public String getStatus() {
        return message.getStatus();
    }

    public String getId() {
        return message.getId();
    }

    public byte[] getCertificateBytes() {
        return message.getCertificateBytes().toByteArray();
    }

    public byte[] getOcspBytes() {
        return message.getOcspBytes().toByteArray();
    }

}
