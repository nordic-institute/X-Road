package ee.cyber.sdsb.common.asic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.MessageFileNames;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Encapsulates the creation of the ASiC-container, which is essentially a
 * ZIP file containing the message and signature.
 */
public class AsicContainer {

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

    /** The name suffix of the hash chain result entry. */
    public static final String ENTRY_HASH_CHAIN_RESULT =
            stripSlash(MessageFileNames.HASH_CHAIN_RESULT);

    /** The name suffix of the hash chain entry. */
    public static final String ENTRY_HASH_CHAIN =
            stripSlash(MessageFileNames.HASH_CHAIN);

    /** The part of the name of the attachment entry. */
    public static final String ENTRY_ATTACHMENT = "-attachment";

    /** Pattern for matching signature file names. */
    private static final Pattern ENTRY_SIGNATURE_PATTERN =
            Pattern.compile("META-INF/.*signatures.*\\.xml");

    /** The SOAP message XML. */
    private String message;

    /** The signature XML and possible hash chain. */
    private SignatureData signature;

    public AsicContainer(String message, SignatureData signature)
            throws Exception {
        this.message = message;
        this.signature = signature;

        verifyContents();
    }

    public String getMessage() {
        return message;
    }

    public SignatureData getSignature() {
        return signature;
    }

    public byte[] getBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTo(out);

        return out.toByteArray();
    }

    public void writeTo(OutputStream out) throws Exception {
        ZipOutputStream zip = new ZipOutputStream(out);
        try {
            // The mime type
            addEntry(zip, ENTRY_MIMETYPE, MIMETYPE);

            // The message (SOAP)
            addEntry(zip, ENTRY_MESSAGE, message);

            // The signature
            addEntry(zip, ENTRY_SIGNATURE, signature.getSignatureXml());

            // The hash chain result and hash chain, if they are supplied
            if (signature.isBatchSignature()) {
                addEntry(zip, ENTRY_HASH_CHAIN_RESULT,
                        signature.getHashChainResult());
                addEntry(zip, ENTRY_HASH_CHAIN, signature.getHashChain());
            }
        } finally {
            zip.close();
        }
    }

    public boolean isAttachment(String fileName) {
        return fileName.startsWith(ENTRY_ATTACHMENT);
    }

    public boolean hasEntry(String fileName) {
        String name = stripSlash(fileName);

        return name.equalsIgnoreCase(ENTRY_MIMETYPE)
                || name.equalsIgnoreCase(ENTRY_MESSAGE)
                || name.equalsIgnoreCase(ENTRY_HASH_CHAIN_RESULT)
                || name.equalsIgnoreCase(ENTRY_HASH_CHAIN)
                || ENTRY_SIGNATURE_PATTERN.matcher(name).matches();
    }

    public InputStream getEntry(String fileName) {
        String name = stripSlash(fileName);

        String data = null;
        if (name.equalsIgnoreCase(ENTRY_MIMETYPE)) {
            data = MIMETYPE;
        } else if (name.equalsIgnoreCase(ENTRY_MESSAGE)) {
            data = message;
        } else if (name.equalsIgnoreCase(ENTRY_SIGNATURE)) {
            data = signature.getSignatureXml();
        } else if (name.equalsIgnoreCase(ENTRY_HASH_CHAIN_RESULT)) {
            data = signature.getHashChainResult();
        } else if (name.equalsIgnoreCase(ENTRY_HASH_CHAIN)) {
            data = signature.getHashChain();
        }

        return data != null ? new ByteArrayInputStream(
                data.getBytes(StandardCharsets.UTF_8)) : null;
    }

    public static AsicContainer read(InputStream is) throws Exception {
        boolean foundMimeType = false;

        String message = null;
        String signature = null;
        String hashChainResult = null;
        String hashChain = null;

        ZipInputStream zip = new ZipInputStream(is);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();

            if (ENTRY_MIMETYPE.equalsIgnoreCase(name) && !foundMimeType) {
                foundMimeType = true;
                verifyMimeType(getData(zip));
                continue;
            }

            if (ENTRY_MESSAGE.equalsIgnoreCase(name)) {
                message = getData(zip);
                continue;
            }

            // The signature must reside in a file whose name contains
            // the string "signatures"
            if (ENTRY_SIGNATURE_PATTERN.matcher(name).matches()) {
                signature = getData(zip);
                continue;
            }

            if (ENTRY_HASH_CHAIN_RESULT.equalsIgnoreCase(name)) {
                hashChainResult = getData(zip);
                continue;
            }

            if (ENTRY_HASH_CHAIN.equalsIgnoreCase(name)) {
                hashChain = getData(zip);
                continue;
            }
        }

        if (!foundMimeType) {
            throw new CodedException(X_ASIC_MIME_TYPE_NOT_FOUND,
                    "Mime type not found");
        }

        return new AsicContainer(message, new SignatureData(signature,
                hashChainResult, hashChain));
    }

    private void verifyContents() throws Exception {
        if (message == null) {
            throw new CodedException(X_ASIC_MESSAGE_NOT_FOUND,
                    "Message not found");
        }

        if (signature == null || signature.getSignatureXml() == null) {
            throw new CodedException(X_ASIC_SIGNATURE_NOT_FOUND,
                    "Signature not found");
        }
    }

    static void verifyMimeType(String data) throws Exception {
        if (!MIMETYPE.equalsIgnoreCase(data)) {
            throw new CodedException(X_ASIC_INVALID_MIME_TYPE,
                    "Invalid mime type: %s", data);
        }
    }

    static void addEntry(ZipOutputStream zip, String name, String data)
            throws IOException {
        addEntry(zip, name, data.getBytes(StandardCharsets.UTF_8));
    }

    static void addEntry(ZipOutputStream zip, String name, byte[] data)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
    }

    static String getData(ZipInputStream zip) throws Exception {
        return IOUtils.toString(zip, StandardCharsets.UTF_8);
    }

    static String stripSlash(String name) {
        if (name.startsWith("/")) {
            return name.substring(1);
        }

        return name;
    }
}
