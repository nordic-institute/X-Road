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

package org.niis.xroad.edc.extension.edr;

import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.niis.xroad.edc.extension.edr.dto.NegotiateAssetRequestDto;
import org.niis.xroad.edc.extension.edr.service.AssetAuthorizationManager;

@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
@Path("/v1/xrd-edr")
@RequiredArgsConstructor
public class XrdEdrController implements XrdEdrApi {

    private final TypeTransformerRegistry transformerRegistry;
    private final AssetAuthorizationManager assetAuthorizationManager;

    @POST
    @Override
    public JsonObject requestAssetAccess(JsonObject dto) {
        NegotiateAssetRequestDto requestDto = transformerRegistry.transform(dto, NegotiateAssetRequestDto.class)
                .orElseThrow(InvalidRequestException::new);

        try {
            var response = assetAuthorizationManager.getOrRequestAssetAccess(requestDto);

            return transformerRegistry.transform(response, JsonObject.class)
                    .orElseThrow(f -> new EdcException("Error creating response body: " + f.getFailureDetail()));
        } catch (Exception e) {
            throw new InvalidRequestException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

}
