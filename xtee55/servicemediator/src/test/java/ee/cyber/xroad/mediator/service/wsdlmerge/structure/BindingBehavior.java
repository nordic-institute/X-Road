package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import javax.xml.namespace.QName;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.mockito.Mockito;

import ee.cyber.xroad.mediator.service.wsdlmerge.TestNS;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.Binding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.BindingOperation;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.DoclitBinding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.DoclitBindingOperation;

import static org.junit.Assert.assertEquals;

/**
 * Covers classes {@link Binding} and {@link BindingOperation}.
 */
public class BindingBehavior {

    /**
     * Tests XML representation of document/literal binding.
     *
     * @throws Exception thrown when binding cannot be turned into XML.
     */
    @Test
    public void shouldTurnDoclitBindingIntoXml() throws Exception {
        // Given
        QName type = new QName(TestNS.XRDDL_TNS, "bindingType");

        Marshallable mockVersionNode = Mockito.mock(Marshallable.class);
        Mockito.when(mockVersionNode.getXml())
                .thenReturn("<xrd:version>v1</xrd:version>");

        List<Marshallable> xrdNodes = Arrays.asList(mockVersionNode);

        List<BindingOperation> operations = new ArrayList<>();
        operations.add(new DoclitBindingOperation("bindingOp", "v1", xrdNodes));

        Binding binding = new DoclitBinding("bindingName", type, operations);

        // When
        String actualXml = binding.getXml();

        // Then
        String expectedXml = FileUtils.readFileToString(new File(
                "src/test/resources/structure/binding-doclit.expected")).trim();

        assertEquals(expectedXml, actualXml);
    }
}
