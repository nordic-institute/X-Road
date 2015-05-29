package ee.cyber.xroad.validator.identifiermapping;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;

final class IdentifierMappingUnmarshaller {

    private IdentifierMappingUnmarshaller() {
    }

    @SuppressWarnings("unchecked")
    public static MappingsType unmarshal(byte[] fileContent) throws Exception {
        JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        try (InputStream is = new ByteArrayInputStream(fileContent)) {
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            JAXBElement<MappingsType> unmarshal =
                    (JAXBElement<MappingsType>) unmarshaller.unmarshal(reader);

            return unmarshal.getValue();
        }
    }
}
