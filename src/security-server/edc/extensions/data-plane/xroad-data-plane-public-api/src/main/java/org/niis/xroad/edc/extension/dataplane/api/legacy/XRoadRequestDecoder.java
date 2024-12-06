package org.niis.xroad.edc.extension.dataplane.api.legacy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;

import jakarta.ws.rs.container.ContainerRequestContext;
import lombok.RequiredArgsConstructor;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;

@RequiredArgsConstructor
public class XRoadRequestDecoder {

    private final GlobalConfProvider globalConfProvider;

    public ProxyMessage readMessage(ContainerRequestContext requestContext) throws Exception {
        var requestMessage = new ProxyMessage(requestContext.getHeaderString(HEADER_ORIGINAL_CONTENT_TYPE));
        var decoder = new ProxyMessageDecoder(globalConfProvider, requestMessage, requestContext.getMediaType().toString(), false, getHashAlgoId(requestContext));
        decoder.parse(requestContext.getEntityStream());

        return requestMessage;
    }

    private DigestAlgorithm getHashAlgoId(ContainerRequestContext request) {
        var hashAlgoId = DigestAlgorithm.ofName(request.getHeaderString(HEADER_HASH_ALGO_ID));
        if (hashAlgoId == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Could not get hash algorithm identifier from message");
        }
        return hashAlgoId;
    }

}
