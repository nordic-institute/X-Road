package ee.cyber.xroad.common.asic;

import java.util.regex.Pattern;

import ee.cyber.xroad.common.util.MessageFileNames;

import static ee.cyber.xroad.common.asic.Helper.stripSlash;

public class AsicContainerEntries {

    /** The default ASiC container file name suffix. */
    public static final String FILENAME_SUFFIX = "-signed-message.asice";

    /** The mime type value of the container. */
    public static final String MIMETYPE = "application/vnd.etsi.asic-e+zip";

    /** The name of the mimetype entry. */
    public static final String ENTRY_MIMETYPE = "mimetype";

    /** The name suffix of the message entry. */
    public static final String ENTRY_MESSAGE =
            stripSlash(MessageFileNames.MESSAGE);

    /** The name of the signature entry. */
    public static final String ENTRY_SIGNATURE = "META-INF/signatures.xml";

    /** The name suffix of the signature hash chain result entry. */
    public static final String ENTRY_SIG_HASH_CHAIN_RESULT =
            AsicContainerEntries.PREFIX_SIG
                + stripSlash(MessageFileNames.HASH_CHAIN_RESULT);

    /** The name suffix of the signature hash chain entry. */
    public static final String ENTRY_SIG_HASH_CHAIN =
            AsicContainerEntries.PREFIX_SIG
                + stripSlash(MessageFileNames.HASH_CHAIN);

    /** The name suffix of the time-stamp hash chain result entry. */
    public static final String ENTRY_TS_HASH_CHAIN_RESULT =
            AsicContainerEntries.PREFIX_TS
                + stripSlash(MessageFileNames.HASH_CHAIN_RESULT);

    /** The name suffix of the time-stamp hash chain entry. */
    public static final String ENTRY_TS_HASH_CHAIN =
            AsicContainerEntries.PREFIX_TS
                + stripSlash(MessageFileNames.HASH_CHAIN);

    /** The part of the name of the attachment entry. */
    public static final String ENTRY_ATTACHMENT = "-attachment";

    /** The name of the manifest file. */
    public static final String ENTRY_MANIFEST =
            "META-INF/manifest.xml";

    /** The name of the manifest file for time-stamped data object. */
    public static final String ENTRY_ASIC_MANIFEST =
            "META-INF/ASiCManifest.xml";

    /** The name of the timestamp file. */
    public static final String ENTRY_TIMESTAMP = "META-INF/timestamp.tst";

    /** Pattern for matching signature file names. */
    static final Pattern ENTRY_SIGNATURE_PATTERN =
            Pattern.compile("META-INF/.*signatures.*\\.xml");

    static final Object[] ALL_ENTRIES = {
        ENTRY_MIMETYPE,
        ENTRY_MESSAGE,
        ENTRY_SIGNATURE_PATTERN,
        ENTRY_SIG_HASH_CHAIN_RESULT,
        ENTRY_SIG_HASH_CHAIN,
        ENTRY_TS_HASH_CHAIN_RESULT,
        ENTRY_TS_HASH_CHAIN,
        ENTRY_MANIFEST,
        ENTRY_ASIC_MANIFEST,
        ENTRY_TIMESTAMP,
    };

    private static final String PREFIX_SIG = "sig-";
    private static final String PREFIX_TS = "ts-";

    private AsicContainerEntries() {
    }

}
