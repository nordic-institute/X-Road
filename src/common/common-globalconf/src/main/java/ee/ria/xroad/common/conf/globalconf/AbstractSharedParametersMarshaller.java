package ee.ria.xroad.common.conf.globalconf;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.SneakyThrows;

import javax.xml.validation.Schema;

import java.io.OutputStream;
import java.io.StringWriter;

abstract class AbstractSharedParametersMarshaller<T> implements SharedParametersMarshaller {
    abstract JAXBContext getJaxbContext();

    abstract Schema getSchema();

    abstract JAXBElement<T> convert(SharedParameters parameters);

    @SneakyThrows
    public String marshall(SharedParameters parameters) {
        var writer = new StringWriter();
        createJaxbMarshaller().marshal(convert(parameters), writer);
        return writer.toString();
    }

    @Override
    @SneakyThrows
    public void marshall(SharedParameters parameters, OutputStream outputStream) {
        createJaxbMarshaller().marshal(convert(parameters), outputStream);
    }

    private Marshaller createJaxbMarshaller() throws JAXBException {
        var marshaller = getJaxbContext().createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setSchema(getSchema());
        return marshaller;
    }


}
