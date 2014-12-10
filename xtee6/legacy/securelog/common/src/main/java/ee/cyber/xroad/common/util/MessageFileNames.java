package ee.cyber.xroad.common.util;

/**
 * Constants and methods that codify how to generate file names
 * for storing messages, attachments and hash chains.
 */
public class MessageFileNames {
    /** Name of the file containing hash chain. */
    public static final String HASH_CHAIN = "/hashchain.xml";
    /** Name of the file containing hash chain result. */
    public static final String HASH_CHAIN_RESULT = "/hashchainresult.xml";

    /** Name of the file containing SOAP message. */
    public static final String MESSAGE = "/message.xml";

    /**
     *  Name of the file containing idx-th attachment.
     *  The attachments are numbered starting from 1.
     */
    public static String attachment(int idx) {
        return "/attachment" + idx;
    }
}
