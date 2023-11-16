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
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import io.vavr.control.Option;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

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
    public static final String NS_REPR =
            "http://x-road.eu/xsd/representation.xsd";

    @CheckConsistency
    @XmlElement(name = "client", required = true, namespace = NS_XROAD)
    private ClientId.Conf client;

    @CheckConsistency
    @XmlElement(name = "service", required = false, namespace = NS_XROAD)
    private ServiceId.Conf service;

    @XmlElement(name = "securityServer", required = false, namespace = NS_XROAD)
    private SecurityServerId.Conf securityServer;

    @CheckConsistency
    @XmlElement(name = "userId", required = false, namespace = NS_XROAD)
    private String userId;

    @CheckConsistency
    @XmlElement(name = "representedParty", required = false, namespace = NS_REPR)
    private RepresentedParty representedParty;

    @CheckConsistency
    @XmlElement(name = "issue", required = false, namespace = NS_XROAD)
    private String issue;

    @CheckConsistency
    @XmlElement(name = "id", required = true, namespace = NS_XROAD)
    private String queryId;

    @XmlElement(name = "requestHash", required = false, namespace = NS_XROAD)
    private RequestHash requestHash;

    @XmlElement(name = "protocolVersion", required = true, namespace = NS_XROAD)
    private ProtocolVersion protocolVersion;

    public void setClient(ClientId clientId) {
        this.client = Option.of(clientId)
                .map(ClientId.Conf::ensure)
                .getOrNull();
    }

    public void setService(ServiceId serviceId) {
        this.service = Option.of(serviceId)
                .map(ServiceId.Conf::ensure)
                .getOrNull();
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).build();
    }

}
