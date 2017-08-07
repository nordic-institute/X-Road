package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.message.SoapBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.soap.SOAPBody;
import java.util.List;

/**
 * Utility for building getSecurityServerMetrics node with listed content.
 */
public class MetricsQueryBuilder implements SoapBuilder.SoapBodyCallback {

    public static final String NS_MONITORING = "http://x-road.eu/xsd/monitoring.xsd";

    List<String> params;

    public MetricsQueryBuilder(List<String> params) {
        this.params = params;
    }

    @Override
    public void create(SOAPBody soapBody) throws Exception {

        Document doc = soapBody.getOwnerDocument();
        Element metricsNode = doc.createElementNS(NS_MONITORING, "getSecurityServerMetrics");
        soapBody.appendChild(metricsNode);

        Element outputSpecNode = doc.createElementNS(NS_MONITORING, "outputSpec");
        metricsNode.appendChild(outputSpecNode);

        for (String paramName : params) {
            Element outputFieldNode1 = doc.createElementNS(NS_MONITORING, "outputField");
            outputFieldNode1.setTextContent(paramName);
            outputSpecNode.appendChild(outputFieldNode1);
        }
    }

}
