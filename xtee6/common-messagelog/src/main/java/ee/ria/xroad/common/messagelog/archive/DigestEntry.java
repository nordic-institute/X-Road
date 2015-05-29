package ee.ria.xroad.common.messagelog.archive;

import lombok.Data;

/**
 * Digest with respective fileName.
 */
@Data
public class DigestEntry {
    private Long id;

    private final String digest;
    private final String fileName;

    String toLinkingInfoEntry() {
        return String.format("%s %s", digest, fileName);
    }

    /**
     * Creates empty digest entry, in case no digest has been created.
     *
     * @return - digest entry with empty value.
     */
    public static DigestEntry empty() {
        return new DigestEntry("", "");
    }
}
