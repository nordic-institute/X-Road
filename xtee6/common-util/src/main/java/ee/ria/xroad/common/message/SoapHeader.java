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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import ee.ria.xroad.common.identifier.SecurityServerId;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * This class represents X-Road SOAP message header.
 */
@Getter
@Setter
@XmlRootElement(name = "Header", namespace = SoapUtils.NS_SOAPENV)
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapHeader {

    public static final String NS_XROAD = "http://x-road.eu/xsd/xroad.xsd";
    public static final String PREFIX_XROAD = "xroad";

    @CheckConsistency
    @XmlElement(name = "client", required = true, namespace = NS_XROAD)
    private ClientId client;

    @CheckConsistency
    @XmlElement(name = "service", required = false, namespace = NS_XROAD)
    private ServiceId service;

    @XmlElement(name = "centralService", required = false, namespace = NS_XROAD)
    private CentralServiceId centralService;

    @XmlElement(name = "securityServer", required = false, namespace = NS_XROAD)
    private SecurityServerId securityServer;

    @CheckConsistency
    @XmlElement(name = "userId", required = false, namespace = NS_XROAD)
    private String userId;

    @CheckConsistency
    @XmlElement(name = "id", required = true, namespace = NS_XROAD)
    private String queryId;

    @XmlElement(name = "requestHash", required = false, namespace = NS_XROAD)
    private RequestHash requestHash;

    @XmlElement(name = "async", required = false, namespace = NS_XROAD)
    private boolean async;

    @XmlElement(name = "protocolVersion", required = true, namespace = NS_XROAD)
    private ProtocolVersion protocolVersion = new ProtocolVersion();

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).build();
    }

}
