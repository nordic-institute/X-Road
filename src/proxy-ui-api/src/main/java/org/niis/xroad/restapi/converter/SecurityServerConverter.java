/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.openapi.model.SecurityServer;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Converter for security server (id) related data between openapi
 * and service domain classes
 */
@Component
public class SecurityServerConverter {

    public static final int SECURITY_SERVER_CODE_INDEX = 3;

    private final ClientConverter clientConverter;

    @Autowired
    public SecurityServerConverter(ClientConverter clientConverter) {
        this.clientConverter = clientConverter;
    }

    /**
     * TO DO: ENCODED_CLIENT_AND_SERVICE_ID_SEPARATOR should be a global constant
     * encoded security server id =
     * <instance_id>:<member_class>:<member_code>:<security_server_code>
     * @param encodedId
     * @return
     */
    public SecurityServerId convertId(String encodedId) {
        validateEncodedString(encodedId);
        int serverCodeSeparatorIndex = encodedId.lastIndexOf(
                ClientConverter.ENCODED_CLIENT_AND_SERVICE_ID_SEPARATOR);
        // items 0,1,2 for a client id of an member (not a subsystem)
        String encodedMemberClientId = encodedId.substring(0, serverCodeSeparatorIndex);
        ClientId memberClientId = clientConverter.convertId(encodedMemberClientId);
        String serverCode = encodedId.substring(serverCodeSeparatorIndex + 1);
        SecurityServerId securityServerId = SecurityServerId.create(memberClientId, serverCode);
        return securityServerId;
    }

    private void validateEncodedString(String encodedId) {
        int separators = FormatUtils.countOccurences(encodedId,
                ClientConverter.ENCODED_CLIENT_AND_SERVICE_ID_SEPARATOR);
        if (separators != SECURITY_SERVER_CODE_INDEX) {
            throw new BadRequestException("Invalid security server id " + encodedId);
        }
    }

    /**
     * Convert securityServerId into encoded id
     * @param securityServerId
     * @return
     */
    public String convertId(SecurityServerId securityServerId) {
        ClientId ownerId = securityServerId.getOwner();
        StringBuffer buffer = new StringBuffer();
        buffer.append(clientConverter.convertId(ownerId));
        buffer.append(ClientConverter.ENCODED_CLIENT_AND_SERVICE_ID_SEPARATOR);
        buffer.append(securityServerId.getServerCode());
        return buffer.toString();
    }

    /**
     * Convert SecurityServerId into SecurityServer
     * @param securityServerId
     * @return
     */
    public SecurityServer convert(SecurityServerId securityServerId) {
        SecurityServer securityServer = new SecurityServer();
        securityServer.setId(convertId(securityServerId));
        securityServer.setInstanceId(securityServerId.getXRoadInstance());
        securityServer.setMemberClass(securityServerId.getMemberClass());
        securityServer.setMemberCode(securityServerId.getMemberCode());
        securityServer.setServerCode(securityServerId.getServerCode());
        return securityServer;
    }

}
