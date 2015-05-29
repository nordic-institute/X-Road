package ee.ria.xroad.proxy.common;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapHeader;

/**
 * WSDL request data that is marshalled to and from the message body.
 */
@Getter
@Setter
@ToString
@XmlRootElement(name = "getWsdl", namespace = SoapHeader.NS_XROAD)
@XmlAccessorType(XmlAccessType.FIELD)
public class WsdlRequestData {

    @XmlElement(name = "serviceCode", required = true,
            namespace = SoapHeader.NS_XROAD)
    private String serviceCode;

    @XmlElement(name = "serviceVersion", required = false,
            namespace = SoapHeader.NS_XROAD)
    private String serviceVersion;

    /**
     * @param client the client
     * @return the service identifier for a specified client
     */
    public ServiceId toServiceId(ClientId client) {
        return ServiceId.create(client, serviceCode, serviceVersion);
    }
}
