/**
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
package org.niis.xroad.cs.admin.core.entity;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.Origin;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;

import static org.niis.xroad.cs.admin.core.entity.AuthenticationCertificateRegistrationRequestEntity.DISCRIMINATOR_VALUE;

@Entity
@NoArgsConstructor
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class AuthenticationCertificateRegistrationRequestEntity extends RequestWithProcessingEntity {
    public static final String DISCRIMINATOR_VALUE = "AuthCertRegRequest";

    private static final int KILOBYTE = 1024;

    @NotNull
    @Column(name = "auth_cert", length = 100 * KILOBYTE)
    @Getter
    @Setter
    private byte[] authCert;

    @Column(name = "address")
    @Getter
    @Setter
    private String address;

    @Override
    @Transient
    public ManagementRequestType getManagementRequestType() {
        return ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST;
    }

    public AuthenticationCertificateRegistrationRequestEntity(Origin origin, SecurityServerId serverId, String comments) {
        super(origin, serverId, comments, new AuthenticationCertificateRegistrationRequestProcessingEntity());
    }

    public AuthenticationCertificateRegistrationRequestEntity(Origin origin, String comments,
                                                              AuthenticationCertificateRegistrationRequestEntity other) {
        super(origin, other.getSecurityServerId(), comments, other.getRequestProcessing());
    }

}
