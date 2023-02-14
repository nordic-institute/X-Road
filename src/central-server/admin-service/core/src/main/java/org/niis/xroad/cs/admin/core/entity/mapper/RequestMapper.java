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
package org.niis.xroad.cs.admin.core.entity.mapper;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.niis.xroad.cs.admin.api.converter.GenericUniDirectionalMapper;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.ClientRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.OwnerChangeRequest;
import org.niis.xroad.cs.admin.api.domain.Request;
import org.niis.xroad.cs.admin.api.domain.RequestWithProcessing;
import org.niis.xroad.cs.admin.core.entity.AuthenticationCertificateDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.AuthenticationCertificateRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.OwnerChangeRequestEntity;
import org.niis.xroad.cs.admin.core.entity.RequestEntity;
import org.niis.xroad.cs.admin.core.entity.RequestWithProcessingEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {ClientIdMapper.class, SecurityServerIdMapper.class})
public interface RequestMapper extends GenericUniDirectionalMapper<RequestEntity, Request> {

    @Override
    default Request toTarget(RequestEntity source) {
        if (source == null) {
            return null;
        }
        if (source instanceof AuthenticationCertificateDeletionRequestEntity) {
            return toDto((AuthenticationCertificateDeletionRequestEntity) source);
        }
        if (source instanceof ClientDeletionRequestEntity) {
            return toDto((ClientDeletionRequestEntity) source);
        }
        if (source instanceof RequestWithProcessingEntity) {
            return toTarget((RequestWithProcessingEntity) source);
        }

        throw new IllegalArgumentException("Cannot map " + source.getClass());
    }

    default RequestWithProcessing toTarget(RequestWithProcessingEntity source) {
        if (source == null) {
            return null;
        }

        if (source instanceof AuthenticationCertificateRegistrationRequestEntity) {
            return toDto((AuthenticationCertificateRegistrationRequestEntity) source);
        }
        if (source instanceof ClientRegistrationRequestEntity) {
            return toDto((ClientRegistrationRequestEntity) source);
        }
        if (source instanceof OwnerChangeRequestEntity) {
            return toDto((OwnerChangeRequestEntity) source);
        }

        throw new IllegalArgumentException("Cannot map " + source.getClass());
    }

    AuthenticationCertificateDeletionRequest toDto(AuthenticationCertificateDeletionRequestEntity source);

    ClientDeletionRequest toDto(ClientDeletionRequestEntity source);

    @Mapping(target = "processingStatus", source = "requestProcessing.status")
    AuthenticationCertificateRegistrationRequest toDto(AuthenticationCertificateRegistrationRequestEntity source);

    @Mapping(target = "processingStatus", source = "requestProcessing.status")
    ClientRegistrationRequest toDto(ClientRegistrationRequestEntity source);

    @Mapping(target = "processingStatus", source = "requestProcessing.status")
    OwnerChangeRequest toDto(OwnerChangeRequestEntity source);

}
