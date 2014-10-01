package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.mockito.Mockito;

import ee.cyber.xroad.mediator.service.wsdlmerge.TestNS;

import static org.junit.Assert.assertEquals;

/**
 * Covers classes {@link Service} and {@link ServicePort}.
 */
public class ServiceBehavior {

    @Test
    public void shouldTurnServiceIntoXml() throws Exception {
        // Given

        QName binding = new QName(TestNS.XRDDL_TNS, "portBinding");

        Marshallable addressNode = Mockito.mock(Marshallable.class);
        Mockito.when(addressNode.getXml())
                .thenReturn("<xrd:address producer=\"andmed\"/>");

        List<Marshallable> xrdNodes = Arrays.asList(addressNode);

        List<ServicePort> ports = Arrays.asList(
                new ServicePort(binding, "servicePortName", xrdNodes));

        Service service = new Service("serviceName", ports);

        // When
        String actualXml = service.getXml();

        // Then
        String expectedXml = FileUtils.readFileToString(new File(
                "src/test/resources/structure/service.expected")).trim();

        assertEquals(expectedXml, actualXml);
    }
}
