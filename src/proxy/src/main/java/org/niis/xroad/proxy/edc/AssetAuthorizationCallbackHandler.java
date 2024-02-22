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

package org.niis.xroad.proxy.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.springframework.stereotype.Component;

import static ee.ria.xroad.common.util.JettyUtils.getTarget;
import static org.eclipse.jetty.server.Request.asInputStream;

/**
 * TODO xroad8
 * <p>
 * Upgrade to Jetty 12, use async.
 * Protect this endpoint, no authorization at this momment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetAuthorizationCallbackHandler extends Handler.Abstract {
    private final ObjectMapper objectMapper = JacksonJsonLd.createObjectMapper();

    private final AuthorizedAssetRegistry authorizedAssetRegistry;
    private final AssetTransferRegistry assetTransferRegistry;


    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        final var target = getTarget(request);
        if (StringUtils.isNotBlank(target) && target.equals("/asset-authorization-callback")) {


            var requestBody = objectMapper.readValue(asInputStream(request), JsonObject.class);
            log.info("Received asset callback request: {}", requestBody);

            var transferId = requestBody.getString("id");
            var assetInTransfer = assetTransferRegistry.getAssetInTransfer(transferId);
            authorizedAssetRegistry.registerAsset(assetInTransfer.clientId(), assetInTransfer.assetId(),
                    new InMemoryAuthorizedAssetRegistry.GrantedAssetInfo(
                            transferId,
                            requestBody.getString("contractId"),
                            requestBody.getString("endpoint"),
                            requestBody.getString("authKey"),
                            requestBody.getString("authCode")));

            callback.succeeded();
            return true;
        }
        return false;
    }
}
