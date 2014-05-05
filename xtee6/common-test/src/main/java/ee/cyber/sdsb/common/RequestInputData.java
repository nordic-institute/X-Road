package ee.cyber.sdsb.common;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Encapsulates necessary information about request
 */
public interface RequestInputData {

    public abstract Pair<String, InputStream> getRequestInput()
            throws IOException;

    public abstract String getQueryName();
}
