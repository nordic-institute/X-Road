package ee.cyber.sdsb.common.hashchain;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reference resolver that is used when verifying hash chains.
 */
public interface HashChainReferenceResolver {

    /**
     * Retrieves given URI.
     */
    InputStream resolve(String uri) throws IOException;

    /**
     * This method should return true, if the data reference with the given URI
     * should be resolved by this resolver. If this method returns false,
     * then this reference will not be resolved and hash values are not
     * verified.
     */
    boolean shouldResolve(String uri, byte[] digestValue);
}
