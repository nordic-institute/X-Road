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

package org.niis.xroad.edc.extension.policy.util;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.experimental.UtilityClass;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.spi.agent.ParticipantAgent;
import org.niis.xroad.restapi.converter.ClientIdConverter;

import java.util.Optional;

@UtilityClass
public class PolicyHelper {

    public static final String CLIENT_ID_KEY = "xroad_client_id";
    private final ClientIdConverter clientIdConverter = new ClientIdConverter();

    public static Optional<String> getClientIdFromContext(PolicyContext context) {
        var participantAgent = context.getContextData(ParticipantAgent.class);

        if (participantAgent != null) {
            var claims = participantAgent.getClaims();
            if (claims.containsKey(CLIENT_ID_KEY)) {
                return Optional.of(claims.get(CLIENT_ID_KEY).toString());
            }
        }

        return Optional.empty();
    }

    public static ClientId parseClientId(String value) {
        return clientIdConverter.convertId(value);
    }

}
