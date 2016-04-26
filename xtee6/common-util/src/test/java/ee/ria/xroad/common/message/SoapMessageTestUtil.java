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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;

/**
 * Test utils for SOAP messages
 */
public final class SoapMessageTestUtil {

    public static final String QUERY_DIR = "../proxy/src/test/queries/";

    /**
     * Constructor
     */
    private SoapMessageTestUtil() {
    }

    /**
     * Build SOAP message
     * @param sender message sender
     * @param receiver message receiver
     * @param userId user id
     * @param queryId query id
     * @return SOAP message
     * @throws Exception when error occurs
     */
    public static SoapMessageImpl build(ClientId sender,
            ServiceId receiver, String userId, String queryId)
                    throws Exception {
        return build(false, sender, receiver, userId, queryId, null);
    }

    /**
     * Build SOAP message
     * @param isRpcEncoded wheter message is rpc encoded
     * @param sender message sender
     * @param receiver message receiver
     * @param userId user id
     * @param queryId query id
     * @return SOAP message
     * @throws Exception when error occurs
     */
    public static SoapMessageImpl build(boolean isRpcEncoded, ClientId sender,
            ServiceId receiver, String userId, String queryId)
                    throws Exception {
        return build(isRpcEncoded, sender, receiver, userId, queryId, null);
    }

    /**
     *
     * @param isRpcEncoded wheter message is rpc encoded
     * @param sender message sender
     * @param receiver message receiver
     * @param userId user id
     * @param queryId query id
     * @param createBodyCallback callback for body creation
     * @return SOAP message
     * @throws Exception when error occurs
     */
    public static SoapMessageImpl build(boolean isRpcEncoded, ClientId sender,
            ServiceId receiver, String userId, String queryId,
            SoapBuilder.SoapBodyCallback createBodyCallback)
                    throws Exception {
        SoapHeader header = new SoapHeader();
        header.setClient(sender);
        header.setService(receiver);
        header.setUserId(userId);
        header.setQueryId(queryId);

        SoapBuilder builder = new SoapBuilder();
        builder.setHeader(header);
        builder.setRpcEncoded(isRpcEncoded);
        builder.setCreateBodyCallback(createBodyCallback);

        return builder.build();
    }

    /**
     * File to bytes
     * @param fileName file
     * @return byte array
     * @throws Exception when error occurs
     */
    public static byte[] fileToBytes(String fileName) throws Exception {
        return IOUtils.toByteArray(newQueryInputStream(fileName));
    }

    /**
     * SOAP message to bytes
     * @param soap SOAP message
     * @return byte array
     * @throws Exception when error occurs
     */
    public static byte[] messageToBytes(Soap soap) throws Exception {
        if (soap instanceof SoapMessage) {
            return ((SoapMessage)soap).getBytes();
        }

        return soap.getXml().getBytes();
    }

    /**
     * Create SOAP message from file
     * @param fileName input file
     * @return SOAP message
     * @throws Exception when error occurs
     */
    public static Soap createSoapMessage(String fileName)
            throws Exception {
        return createSoapMessage(QUERY_DIR, fileName);
    }

    /**
     * Create SOAP message from file
     * @param queryDir query directory
     * @param fileName file
     * @return SOAP message
     * @throws Exception when error occurs
     */
    public static Soap createSoapMessage(String queryDir, String fileName)
            throws Exception {
        return new SoapParserImpl().parse(newQueryInputStream(queryDir, fileName));
    }

    /**
     * Create SOAP message from byte array
     * @param data byte array
     * @return SOAP message
     * @throws Exception when error occurs
     */
    public static Soap createSoapMessage(byte[] data)
            throws Exception {
        return new SoapParserImpl().parse(new ByteArrayInputStream(data));
    }

    /**
     * Create SOAP request from file
     * @param fileName input file
     * @return SOAP request
     * @throws Exception when error occurs
     */
    public static SoapMessageImpl createRequest(String fileName)
            throws Exception {
        return createRequest(QUERY_DIR, fileName);
    }

    /**
     * Create SOAP request
     * @param queryDir query directory
     * @param fileName input file
     * @return SOAP request
     * @throws Exception when error occurs
     */
    public static SoapMessageImpl createRequest(String queryDir, String fileName)
            throws Exception {
        Soap message = createSoapMessage(queryDir, fileName);
        if (!(message instanceof SoapMessageImpl)) {
            throw new RuntimeException(
                    "Got " + message.getClass() + " instead of SoapMessage");
        }

        if (((SoapMessageImpl) message).isResponse()) {
            throw new RuntimeException("Got response instead of request");
        }

        return (SoapMessageImpl) message;
    }

    /**
     * Create SOAP response from file
     * @param fileName input file
     * @return SOAP response
     * @throws Exception when error occurs
     */
    public static SoapMessageImpl createResponse(String fileName)
            throws Exception {
        return createResponse(QUERY_DIR, fileName);
    }

    /**
     * Create SOAP response
     * @param queryDir query directory
     * @param fileName input file
     * @return SOAP response
     * @throws Exception when error occurs
     */
    public static SoapMessageImpl createResponse(String queryDir, String fileName)
            throws Exception {
        Soap message = createSoapMessage(queryDir, fileName);
        if (!(message instanceof SoapMessageImpl)) {
            throw new RuntimeException(
                    "Got " + message.getClass() + " instead of SoapResponse");
        }

        if (((SoapMessageImpl) message).isRequest()) {
            throw new RuntimeException("Got request instead of response");
        }

        return (SoapMessageImpl) message;
    }

    /**
     * Create query input stream
     * @param fileName file
     * @return input stream
     * @throws Exception when error occurs
     */
    public static FileInputStream newQueryInputStream(String fileName)
            throws Exception {
        return newQueryInputStream(QUERY_DIR, fileName);
    }

    /**
     * Create new query input stream
     * @param queryDir query directory
     * @param fileName input file
     * @return input stream
     * @throws Exception when error occurs
     */
    public static FileInputStream newQueryInputStream(String queryDir, String fileName)
            throws Exception {
        return new FileInputStream(queryDir + fileName);
    }
}
