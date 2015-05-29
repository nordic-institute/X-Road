package ee.ria.xroad.common.signature;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;

/**
 * Default hash chain reference resolver implementation.
 */
public class DefaultHashChainReferenceResolver
        implements HashChainReferenceResolver {

    @Override
    public InputStream resolve(String uri) throws IOException {
        return new FileInputStream(uri);
    }

    @Override
    public boolean shouldResolve(String uri, byte[] digestValue) {
        return true;
    }

}
