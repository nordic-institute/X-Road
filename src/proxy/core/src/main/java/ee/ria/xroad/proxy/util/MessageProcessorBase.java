/*
 * The MIT License
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
package ee.ria.xroad.proxy.util;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.common.util.UriUtils;
import ee.ria.xroad.proxy.conf.KeyConfProvider;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.IsAuthenticationData;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SOAPACTION;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * Base class for message processors.
 */
@Slf4j
public abstract class MessageProcessorBase {

    protected final GlobalConfProvider globalConfProvider;
    protected final KeyConfProvider keyConfProvider;
    protected final ServerConfProvider serverConfProvider;

    protected final CertChainFactory certChainFactory;
    protected final CertHelper certHelper;

    /**
     * The servlet request.
     */
    protected final RequestWrapper jRequest;

    /**
     * The servlet response.
     */
    protected final ResponseWrapper jResponse;

    /**
     * The http client instance.
     */
    protected final HttpClient httpClient;

    protected MessageProcessorBase(GlobalConfProvider globalConfProvider,
                                   KeyConfProvider keyConfProvider,
                                   ServerConfProvider serverConfProvider,
                                   CertChainFactory certChainFactory,
                                   RequestWrapper request,
                                   ResponseWrapper response,
                                   HttpClient httpClient) {
        this.globalConfProvider = globalConfProvider;
        this.certHelper = new CertHelper(globalConfProvider);
        this.keyConfProvider = keyConfProvider;
        this.serverConfProvider = serverConfProvider;
        this.certChainFactory = certChainFactory;
        this.jRequest = request;
        this.jResponse = response;
        this.httpClient = httpClient;

        globalConfProvider.verifyValidity();
    }

    /**
     * Returns a new instance of http sender.
     */
    protected HttpSender createHttpSender() {
        return new HttpSender(httpClient);
    }

    /**
     * Called when processing started.
     */
    protected void preprocess() throws Exception {
    }

    /**
     * Called when processing successfully completed.
     */
    protected void postprocess() throws Exception {
    }

    /**
     * Processes the incoming message.
     *
     * @throws Exception in case of any errors
     */
    public abstract void process() throws Exception;

    /**
     * Update operational monitoring data with SOAP message header data and
     * the size of the message.
     *
     * @param opMonitoringData monitoring data to update
     * @param soapMessage      SOAP message
     */
    protected static void updateOpMonitoringDataBySoapMessage(
            OpMonitoringData opMonitoringData, SoapMessageImpl soapMessage) {
        if (opMonitoringData != null && soapMessage != null) {
            opMonitoringData.setClientId(soapMessage.getClient());
            opMonitoringData.setServiceId(soapMessage.getService());
            opMonitoringData.setMessageId(soapMessage.getQueryId());
            opMonitoringData.setMessageUserId(soapMessage.getUserId());
            opMonitoringData.setMessageIssue(soapMessage.getIssue());
            opMonitoringData.setRepresentedParty(
                    soapMessage.getRepresentedParty());
            opMonitoringData.setMessageProtocolVersion(
                    soapMessage.getProtocolVersion());
            opMonitoringData.setServiceType(DescriptionType.WSDL.name());
            opMonitoringData.setRequestSize(soapMessage.getBytes().length);
        }
    }

    /**
     * Update operational monitoring data with REST message header data
     */
    protected void updateOpMonitoringDataByRestRequest(OpMonitoringData opMonitoringData, RestRequest request) {
        if (opMonitoringData != null && request != null) {
            opMonitoringData.setClientId(request.getSender());
            opMonitoringData.setServiceId(request.getServiceId());
            opMonitoringData.setMessageId(request.getQueryId());
            opMonitoringData.setMessageUserId(request.findHeaderValueByName(MimeUtils.HEADER_USER_ID));
            opMonitoringData.setMessageIssue(request.findHeaderValueByName(MimeUtils.HEADER_ISSUE));
            opMonitoringData.setRepresentedParty(request.getRepresentedParty());
            opMonitoringData.setMessageProtocolVersion(String.valueOf(request.getVersion()));
            opMonitoringData.setServiceType(Optional.ofNullable(
                    serverConfProvider.getDescriptionType(request.getServiceId())).orElse(DescriptionType.REST).name());
            opMonitoringData.setRestMethod(request.getVerb().name());
            opMonitoringData.setRestPath(getNormalizedServicePath(request.getServicePath()));
        }
    }

    private String getNormalizedServicePath(String servicePath) {
        return Optional.of(UriUtils.uriPathPercentDecode(URI.create(servicePath).normalize().getRawPath(), true))
                .orElse(servicePath);
    }

    /**
     * Check that message transfer was successful.
     */
    public boolean verifyMessageExchangeSucceeded() {
        return true;
    }

    protected String getSecurityServerAddress() {
        return globalConfProvider.getSecurityServerAddress(serverConfProvider.getIdentifier());
    }

    /**
     * Validates SOAPAction header value.
     * Valid header values are: (empty string),(""),("URI-reference")
     * In addition, this implementation allows missing (null) header.
     *
     * @return the argument as-is if it is valid
     * @throws CodedException if the the argument is invalid
     * @see <a href="https://www.w3.org/TR/2000/NOTE-SOAP-20000508/#_Toc478383528">SOAP 1.1</a>
     */
    protected static String validateSoapActionHeader(String soapAction) {
        if (soapAction == null || "".equals(soapAction) || "\"\"".equals(soapAction)) {
            //allow missing, empty and "" SoapAction
            return soapAction;
        }

        final int lastIndex = soapAction.length() - 1;
        if (lastIndex > 1 && soapAction.charAt(0) == '"' && soapAction.charAt(lastIndex) == '"') {
            try {
                // try to parse the URI, ignore result
                new URI(soapAction.substring(1, lastIndex));
                return soapAction;
            } catch (URISyntaxException e) {
                throw new CodedException(X_INVALID_SOAPACTION, e, "Malformed SOAPAction header");
            }
        }
        throw new CodedException(X_INVALID_SOAPACTION, "Malformed SOAPAction header");
    }

    /**
     * Logs a warning if identifier contains invalid characters.
     *
     * @see ee.ria.xroad.common.validation.SpringFirewallValidationRules
     * @see ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator;
     */
    protected static boolean checkIdentifier(final XRoadId id) {
        if (id != null) {
            if (!validateIdentifierField(id.getXRoadInstance())) {
                log.warn("Invalid character(s) in identifier {}", id);
                return false;
            }

            for (String f : id.getFieldsForStringFormat()) {
                if (f != null && !validateIdentifierField(f)) {
                    log.warn("Invalid character(s) in identifier {}", id);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Verifies the authentication for the client certificate.
     *
     * @param client the client identifier
     * @param auth   the authentication data of the information system
     * @throws Exception if verification fails
     */
    protected void verifyClientAuthentication(ClientId client,
                                              IsAuthenticationData auth) throws Exception {

        IsAuthentication isAuthentication = serverConfProvider.getIsAuthentication(client);
        if (isAuthentication == null) {
            // Means the client was not found in the server conf.
            // The getIsAuthentication method implemented in ServerConfCommonImpl
            // checks if the client exists; if it does, returns the
            // isAuthentication value or NOSSL if no value is specified.
            throw new CodedException(X_INTERNAL_ERROR,
                    "Client '%s' not found", client);
        }

        log.trace("IS authentication for client '{}' is: {}", client,
                isAuthentication);

        if (isAuthentication == IsAuthentication.SSLNOAUTH
                && auth.isPlaintextConnection()) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Client (%s) specifies HTTPS NO AUTH but client made plaintext connection", client);
        } else if (isAuthentication == IsAuthentication.SSLAUTH) {
            if (auth.cert() == null) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) specifies HTTPS but did not supply"
                                + " TLS certificate", client);
            }

            if (auth.cert().equals(serverConfProvider.getSSLKey().getCertChain()[0])) {
                // do not check certificates for local TLS connections
                return;
            }

            List<X509Certificate> isCerts = serverConfProvider.getIsCerts(client);
            if (isCerts.isEmpty()) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) has no IS certificates", client);
            }

            if (!isCerts.contains(auth.cert())) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) TLS certificate does not match any"
                                + " IS certificates", client);
            }

            clientIsCertPeriodValidatation(client, auth.cert());
        }
    }

    private void clientIsCertPeriodValidatation(ClientId client, X509Certificate cert) throws CodedException {
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            if (SystemProperties.isClientIsCertValidityPeriodCheckEnforced()) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) TLS certificate is expired", client);
            } else {
                log.warn("Client {} TLS certificate is expired", client);
            }
        } catch (CertificateNotYetValidException e) {
            if (SystemProperties.isClientIsCertValidityPeriodCheckEnforced()) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) TLS certificate is not yet valid", client);
            } else {
                log.warn("Client {} TLS certificate is not yet valid", client);
            }
        }
    }

    private static boolean validateIdentifierField(final CharSequence field) {
        for (int i = 0; i < field.length(); i++) {
            final char c = field.charAt(i);
            //ISO control char
            if (c <= '\u001f' || (c >= '\u007f' && c <= '\u009f')) {
                return false;
            }
            //Forbidden chars
            if (c == '%' || c == ':' || c == ';' || c == '/' || c == '\\' || c == '\u200b' || c == '\ufeff') {
                return false;
            }
            //"normalized path" check is redundant since path separators (/,\) are forbidden
        }
        return true;
    }

}
