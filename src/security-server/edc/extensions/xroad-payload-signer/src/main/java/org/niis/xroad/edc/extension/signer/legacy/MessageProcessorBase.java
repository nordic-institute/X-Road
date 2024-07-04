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

package org.niis.xroad.edc.extension.signer.legacy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.util.HttpSender;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.apache.http.client.HttpClient;
import org.eclipse.edc.spi.monitor.Monitor;

import java.net.URI;
import java.net.URISyntaxException;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SOAPACTION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;

public abstract class MessageProcessorBase {

    /**
     * The servlet request.
     */
    protected final ContainerRequestContext requestContext;

    /**
     * The http client instance.
     */
    protected final HttpClient httpClient;
    protected final Monitor monitor;

    protected MessageProcessorBase(ContainerRequestContext request,
                                   HttpClient httpClient,
                                   Monitor monitor) {
        this.requestContext = request;
        this.httpClient = httpClient;
        this.monitor = monitor;

        GlobalConf.verifyValidity();
    }

    /**
     * Processes the incoming message.
     *
     * @throws Exception in case of any errors
     */
    public abstract Response process() throws Exception;


    /**
     * Returns a new instance of http sender.
     */
    protected HttpSender createHttpSender() {
        return new HttpSender(httpClient);
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
    protected String validateSoapActionHeader(String soapAction) {
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
    public boolean checkIdentifier(final XRoadId id) {
        if (id != null) {
            if (!validateIdentifierField(id.getXRoadInstance())) {
                monitor.warning(() -> "Invalid character(s) in identifier %s".formatted(id));
                return false;
            }

            for (String f : id.getFieldsForStringFormat()) {
                if (f != null && !validateIdentifierField(f)) {
                    monitor.warning(() -> "Invalid character(s) in identifier %s".formatted(id));
                    return false;
                }
            }
        }
        return true;
    }

    private boolean validateIdentifierField(final CharSequence field) {
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

    protected String getHashAlgoId(ContainerRequestContext request) {
        String hashAlgoId = request.getHeaderString(HEADER_HASH_ALGO_ID);

        if (hashAlgoId == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Could not get hash algorithm identifier from message");
        }

        return hashAlgoId;
    }

}
