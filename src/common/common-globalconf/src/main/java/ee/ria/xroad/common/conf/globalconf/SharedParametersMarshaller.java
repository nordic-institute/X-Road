package ee.ria.xroad.common.conf.globalconf;

import java.io.OutputStream;

public interface SharedParametersMarshaller {

    String marshall(SharedParameters parameters);

    void marshall(SharedParameters parameters, OutputStream outputStream);
}
