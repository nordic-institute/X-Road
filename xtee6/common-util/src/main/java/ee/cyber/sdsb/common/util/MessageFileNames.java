package ee.cyber.sdsb.common.util;

/**
 * Constants and methods that codify how to generate file names
 * for storing messages, attachments and hash chains.
 */
public class MessageFileNames {

    /** Name of the file containing hash chain. */
    public static final String SIG_HASH_CHAIN = "/sig-hashchain.xml";

    /** Name of the file containing hash chain result. */
    public static final String SIG_HASH_CHAIN_RESULT =
            "/sig-hashchainresult.xml";

    /** Name of the file containing hash chain. */
    public static final String TS_HASH_CHAIN = "/ts-hashchain.xml";

    /** Name of the file containing hash chain result. */
    public static final String TS_HASH_CHAIN_RESULT =
            "/ts-hashchainresult.xml";

    /** Name of the file containing SOAP message. */
    public static final String MESSAGE = "/message.xml";

    /** Name of the file containing SOAP message. */
    public static final String SIGNATURE = "/META-INF/signatures.xml";

    /**
     *  Name of the file containing idx-th attachment.
     *  The attachments are numbered starting from 1.
     */
    public static String attachment(int idx) {
        return "/attachment" + idx;
    }
}
