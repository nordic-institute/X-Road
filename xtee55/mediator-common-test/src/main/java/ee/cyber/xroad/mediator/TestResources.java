package ee.cyber.xroad.mediator;

import java.io.InputStream;

import ee.cyber.sdsb.common.util.ResourceUtils;

public class TestResources {

    public static InputStream get(String fileName) throws Exception {
        if (fileName != null) {
            InputStream is = ResourceUtils.getClasspathResourceStream(fileName);
            if (is == null) {
                throw new RuntimeException("Failed to get classpath resource '"
                        + fileName + "' as stream");
            }

            return is;
        }

        return null;
    }
}
