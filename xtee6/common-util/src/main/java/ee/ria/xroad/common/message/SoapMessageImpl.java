/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import javax.xml.soap.SOAPMessage;

import static ee.ria.xroad.common.message.SoapUtils.isResponseMessage;
import static ee.ria.xroad.common.message.SoapUtils.isRpcMessage;

/**
 * This class represents the XROAD SOAP message.
 */
public class SoapMessageImpl extends AbstractSoapMessage<SoapHeader> {

    SoapMessageImpl(byte[] rawXml, String charset, SoapHeader header,
                    SOAPMessage soap, String serviceName) throws Exception {
        super(rawXml, charset, header, soap, isResponseMessage(serviceName),
                isRpcMessage(soap));
    }

    /**
     * Gets the client ID in the SOAP message header.
     *
     * @return ClientId
     */
    public ClientId getClient() {
        return getHeader().getClient();
    }

    /**
     * Gets the service ID in the SOAP message header.
     *
     * @return ServiceId
     */
    public ServiceId getService() {
        return getHeader().getService();
    }

    /**
     * Gets the central service ID in the SOAP message header.
     *
     * @return CentralServiceId
     */
    public CentralServiceId getCentralService() {
        return getHeader().getCentralService();
    }

    /**
     * Gets the security server ID in the SOAP message header.
     * @return SecurityServerId
     */
    public SecurityServerId getSecurityServer() {
        return getHeader().getSecurityServer();
    }


    /**
     * True if the SOAP message is marked as asynchronous.
     *
     * @return boolean
     */
    public boolean isAsync() {
        return getHeader().isAsync();
    }

    /**
     * Gets the query ID from the SOAP message header.
     *
     * @return String
     */
    public String getQueryId() {
        return getHeader().getQueryId();
    }

    /**
     * Gets the user ID from the SOAP message header.
     *
     * @return String
     */
    public String getUserId() {
        return getHeader().getUserId();
    }

}

