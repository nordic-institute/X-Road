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
package org.niis.xroad.common.managementrequest.verify;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.request.AddressChangeRequestType;
import ee.ria.xroad.common.request.AuthCertDeletionRequestType;
import ee.ria.xroad.common.request.AuthCertRegRequestType;
import ee.ria.xroad.common.request.ClientRequestType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.common.managementrequest.verify.decode.AddressChangeRequestCallback;
import org.niis.xroad.common.managementrequest.verify.decode.AuthCertDeletionRequestDecoderCallback;
import org.niis.xroad.common.managementrequest.verify.decode.AuthCertRegRequestDecoderCallback;
import org.niis.xroad.common.managementrequest.verify.decode.ClientDeletionRequestCallback;
import org.niis.xroad.common.managementrequest.verify.decode.ClientDisableRequestCallback;
import org.niis.xroad.common.managementrequest.verify.decode.ClientEnableRequestCallback;
import org.niis.xroad.common.managementrequest.verify.decode.ClientRegRequestCallback;
import org.niis.xroad.common.managementrequest.verify.decode.ManagementRequestDecoderCallback;
import org.niis.xroad.common.managementrequest.verify.decode.OwnerChangeRequestCallback;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_OUTDATED_GLOBALCONF;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.AUTH_CERT_DELETION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST;

/**
 * Reads and verifies management requests.
 */
@Slf4j
@NoArgsConstructor
public final class ManagementRequestVerifier {


    public static class Result {
        @Getter
        private final SoapMessageImpl soapMessage;
        @Getter
        private final ManagementRequestType requestType;

        private final AuthCertRegRequestType authCertRegRequest;
        private final AuthCertDeletionRequestType authCertDeletionRequestType;
        private final ClientRequestType clientRequest;
        private final AddressChangeRequestType addressChangeRequest;

        public Result(SoapMessageImpl soapMessage, AuthCertRegRequestType authCertRegRequest) {
            this.soapMessage = soapMessage;
            this.requestType = AUTH_CERT_REGISTRATION_REQUEST;
            this.authCertRegRequest = authCertRegRequest;
            this.authCertDeletionRequestType = null;
            this.addressChangeRequest = null;
            this.clientRequest = null;
        }

        public Result(SoapMessageImpl soapMessage, AuthCertDeletionRequestType authCertDeletionRequestType) {
            this.soapMessage = soapMessage;
            this.requestType = AUTH_CERT_DELETION_REQUEST;
            this.authCertRegRequest = null;
            this.authCertDeletionRequestType = authCertDeletionRequestType;
            this.clientRequest = null;
            this.addressChangeRequest = null;
        }

        public Result(SoapMessageImpl soapMessage, ManagementRequestType requestType, ClientRequestType clientRequest) {
            this.soapMessage = soapMessage;
            this.requestType = requestType;
            this.authCertRegRequest = null;
            this.authCertDeletionRequestType = null;
            this.addressChangeRequest = null;
            this.clientRequest = clientRequest;
        }

        public Result(SoapMessageImpl soapMessage, AddressChangeRequestType addressChangeRequest) {
            this.soapMessage = soapMessage;
            this.requestType = ManagementRequestType.ADDRESS_CHANGE_REQUEST;
            this.addressChangeRequest = addressChangeRequest;
            this.clientRequest = null;
            this.authCertDeletionRequestType = null;
            this.authCertRegRequest = null;
        }

        public Optional<AuthCertRegRequestType> getAuthCertRegRequest() {
            return Optional.ofNullable(authCertRegRequest);
        }

        public Optional<AuthCertDeletionRequestType> getAuthCertDeletionRequest() {
            return Optional.ofNullable(authCertDeletionRequestType);
        }

        public Optional<AddressChangeRequestType> getAddressChangeRequest() {
            return Optional.ofNullable(addressChangeRequest);
        }

        public Optional<ClientRequestType> getClientRequest() {
            return Optional.ofNullable(clientRequest);
        }
    }

    /**
     * Reads management requests from input stream.
     *
     * @param contentType expected content type of the stream
     * @param inputStream the input stream
     * @return management request message
     * @throws Exception in case of any errors
     */
    public static Result readRequest(String contentType, InputStream inputStream) throws Exception {

        if (!GlobalConf.isValid()) {
            throw new CodedException(X_OUTDATED_GLOBALCONF, "Global configuration is not valid");
        }

        DecoderCallback cb = new DecoderCallback();

        SoapMessageDecoder decoder = new SoapMessageDecoder(contentType, cb);
        decoder.parse(inputStream);

        return buildResult(cb);
    }

    private static Result buildResult(DecoderCallback cb) {
        if (cb.getManagementRequestDecoderCallback() == null) {
            throw new CodedException(X_INVALID_REQUEST, "Failed to parse SOAP request. Decoder was not fully initialized.");
        }
        Object request = cb.getManagementRequestDecoderCallback().getRequest();
        if (request == null) {
            throw new CodedException(X_INVALID_REQUEST, "Failed to parse SOAP request");
        }

        if (request instanceof AuthCertRegRequestType authCertRegRequestType) {
            return new Result(cb.getSoapMessage(), authCertRegRequestType);
        }
        if (request instanceof AuthCertDeletionRequestType authCertDeletionRequestType) {
            return new Result(cb.getSoapMessage(), authCertDeletionRequestType);
        }
        if (request instanceof ClientRequestType clientRequestType) {
            return new Result(cb.getSoapMessage(), cb.getRequestType(), clientRequestType);
        }
        if (request instanceof AddressChangeRequestType addressChangeRequestType) {
            return new Result(cb.getSoapMessage(), addressChangeRequestType);
        }

        throw new CodedException(X_INVALID_REQUEST, "Unrecognized soap request of type '%s'",
                request.getClass().getSimpleName());
    }

    @Getter
    public static class DecoderCallback implements SoapMessageDecoder.Callback {
        private SoapMessageImpl soapMessage;
        private ManagementRequestType requestType;

        private ManagementRequestDecoderCallback managementRequestDecoderCallback;

        @Override
        public void soap(SoapMessage message, Map<String, String> additionalHeaders) {
            this.soapMessage = (SoapMessageImpl) message;
            this.requestType = ManagementRequestType.getByServiceCode(soapMessage.getService().getServiceCode());

            managementRequestDecoderCallback = switch (requestType) {
                case AUTH_CERT_REGISTRATION_REQUEST -> new AuthCertRegRequestDecoderCallback(this);
                case CLIENT_REGISTRATION_REQUEST -> new ClientRegRequestCallback(this);
                case OWNER_CHANGE_REQUEST -> new OwnerChangeRequestCallback(this);
                case CLIENT_DELETION_REQUEST -> new ClientDeletionRequestCallback(this);
                case AUTH_CERT_DELETION_REQUEST -> new AuthCertDeletionRequestDecoderCallback(this);
                case ADDRESS_CHANGE_REQUEST -> new AddressChangeRequestCallback(this);
                case CLIENT_DISABLE_REQUEST -> new ClientDisableRequestCallback(this);
                case CLIENT_ENABLE_REQUEST -> new ClientEnableRequestCallback(this);
                default -> throw new CodedException(X_INVALID_REQUEST, "Unsupported request type %s", requestType);
            };
        }

        @Override
        public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
                throws Exception {
            if (managementRequestDecoderCallback != null) {
                managementRequestDecoderCallback.attachment(content, additionalHeaders);
            } else {
                log.error("attachment was called but decoder callback is not initialized.");
            }
        }

        @Override
        public void onCompleted() {
            if (managementRequestDecoderCallback != null) {
                managementRequestDecoderCallback.onCompleted();
            } else {
                log.error("OnCompleted was called but decoder callback is not initialized.");
            }
        }

        @Override
        public void fault(SoapFault fault) {
            onError(fault.toCodedException());
        }

        @Override
        public void onError(Exception t) {
            throw translateException(t);
        }

    }
}
