package ee.ria.xroad.commonui;

import lombok.Value;

/**
 * Encapsulates optional configuration part filename and content identifier.
 */
@Value
public class OptionalConfPart {
    private String fileName;
    private String contentIdentifier;
}
