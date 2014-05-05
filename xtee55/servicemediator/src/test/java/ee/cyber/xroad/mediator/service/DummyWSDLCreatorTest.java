package ee.cyber.xroad.mediator.service;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.wsdl.Definition;
import javax.wsdl.factory.WSDLFactory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DummyWSDLCreatorTest {

    @Test
    public void createAndParseWSDL() throws Exception {
        String url = "http://localhost:1234";
        List<String> methods = Arrays.asList("aa.foo", "aa.bar", "aa.baz");

        Definition wsdl = new DummyWSDLCreator(url).create(methods);

        StringWriter sw = new StringWriter();
        WSDLFactory wsdlFactory =
                WSDLFactory.newInstance("com.ibm.wsdl.factory.WSDLFactoryImpl");
        wsdlFactory.newWSDLWriter().writeWSDL(wsdl, sw);

        Collection<WSDLParser.ServiceInfo> actualServiceInfo =
                WSDLParser.parseWSDL(
                        new ByteArrayInputStream(sw.toString().getBytes()));
        for (WSDLParser.ServiceInfo service : actualServiceInfo) {
            assertTrue(methods.contains("aa." + service.name));
            assertEquals(url, service.url);
        }
    }

}
