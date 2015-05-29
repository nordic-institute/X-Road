package ee.ria.xroad.common.hashchain;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reference resolver that is used when verifying hash chains.
 */
public interface HashChainReferenceResolver {

    /**
     * Retrieves given URI.
     * @param uri data location
     * @return input stream of the data at the given URI
     * @throws IOException if data could not be read from the given URI
     */
    InputStream resolve(String uri) throws IOException;

    /**
     * This method should return true, if the data reference with the given URI
     * should be resolved by this resolver. If this method returns false,
     * then this reference will not be resolved and hash values are not
     * verified.
     * @param uri URI of the data reference
     * @param digestValue hash of the data
     * @return true, if the data reference with the given URI
     * should be resolved by this resolver, false otherwise
     */
    boolean shouldResolve(String uri, byte[] digestValue);
}
