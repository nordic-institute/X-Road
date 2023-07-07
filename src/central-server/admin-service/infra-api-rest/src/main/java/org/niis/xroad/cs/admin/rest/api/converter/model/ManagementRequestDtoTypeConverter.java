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
package org.niis.xroad.cs.admin.rest.api.converter.model;

import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

@Mapper(componentModel = SPRING)
public interface ManagementRequestDtoTypeConverter {

    @ValueMapping(source = "AUTH_CERT_REGISTRATION_REQUEST", target = "AUTH_CERT_REGISTRATION_REQUEST")
    @ValueMapping(source = "CLIENT_REGISTRATION_REQUEST", target = "CLIENT_REGISTRATION_REQUEST")
    @ValueMapping(source = "OWNER_CHANGE_REQUEST", target = "OWNER_CHANGE_REQUEST")
    @ValueMapping(source = "CLIENT_DELETION_REQUEST", target = "CLIENT_DELETION_REQUEST")
    @ValueMapping(source = "AUTH_CERT_DELETION_REQUEST", target = "AUTH_CERT_DELETION_REQUEST")
    ManagementRequestTypeDto convert(ManagementRequestType source);

    @ValueMapping(source = "AUTH_CERT_REGISTRATION_REQUEST", target = "AUTH_CERT_REGISTRATION_REQUEST")
    @ValueMapping(source = "CLIENT_REGISTRATION_REQUEST", target = "CLIENT_REGISTRATION_REQUEST")
    @ValueMapping(source = "OWNER_CHANGE_REQUEST", target = "OWNER_CHANGE_REQUEST")
    @ValueMapping(source = "CLIENT_DELETION_REQUEST", target = "CLIENT_DELETION_REQUEST")
    @ValueMapping(source = "AUTH_CERT_DELETION_REQUEST", target = "AUTH_CERT_DELETION_REQUEST")
    ManagementRequestType convert(ManagementRequestTypeDto source);
}
