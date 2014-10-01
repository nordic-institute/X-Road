package ee.cyber.sdsb.centerui;

import java.io.File;
import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.FileUtils;

import ee.cyber.sdsb.centerui.identifiermapping.MappingsType;
import ee.cyber.sdsb.centerui.identifiermapping.ObjectFactory;

public class IdentifierMappingUnmarshaller {
    public static void main(String[] args) throws Exception {
        String xml = FileUtils.readFileToString(new File(
                "test/resources/identifiermapping.xml"));
        System.out.println(unmarshal(xml));
    }

    @SuppressWarnings("unchecked")
    public static MappingsType unmarshal(String xml) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        StringReader reader = new StringReader(xml);

        JAXBElement<MappingsType> unmarshal =
                (JAXBElement<MappingsType>) unmarshaller.unmarshal(reader);
        return unmarshal.getValue();
    }
}
