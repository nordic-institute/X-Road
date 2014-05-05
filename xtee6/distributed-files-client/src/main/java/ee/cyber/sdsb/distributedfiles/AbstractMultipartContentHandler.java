package ee.cyber.sdsb.distributedfiles;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;

abstract class AbstractMultipartContentHandler extends AbstractContentHandler {

    private final MimeStreamParser parser;

    private Map<String, String> headers;

    AbstractMultipartContentHandler(MimeStreamParser parser) {
        this.parser = parser;
    }

    @Override
    public final void startMultipart(BodyDescriptor bd) {
        parser.setFlat();
    }

    @Override
    public void startHeader() throws MimeException {
        headers = new HashMap<>();
    }

    @Override
    public void field(Field field) throws MimeException {
        headers.put(field.getName().toLowerCase(), field.getBody());
    }

    protected String getHeader(String headerName) {
        return headers.get(headerName.toLowerCase());
    }
}
