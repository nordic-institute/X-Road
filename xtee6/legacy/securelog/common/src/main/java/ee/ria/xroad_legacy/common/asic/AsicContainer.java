package ee.ria.xroad_legacy.common.asic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import org.eclipse.jetty.http.MimeTypes;

import ee.ria.xroad_legacy.common.signature.SignatureData;

import static ee.ria.xroad_legacy.common.asic.AsicContainerEntries.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Encapsulates the creation of the ASiC-container, which is essentially a
 * ZIP file containing the message and signature.
 */
public class AsicContainer {

    /** Holds the entries in the container. */
    private final Map<String, String> entries = new HashMap<>();

    AsicContainer(Map<String, String> entries) throws Exception {
        this.entries.putAll(entries);
        verifyContents();
    }

    public AsicContainer(String message, SignatureData signature)
            throws Exception {
        put(ENTRY_MIMETYPE, MIMETYPE);
        put(ENTRY_MESSAGE, message);
        put(ENTRY_SIGNATURE, signature.getSignatureXml());
        put(ENTRY_SIG_HASH_CHAIN_RESULT, signature.getHashChainResult());
        put(ENTRY_SIG_HASH_CHAIN, signature.getHashChain());

        createManifests();
        verifyContents();
    }

    public String getMessage() {
        return get(ENTRY_MESSAGE);
    }

    public SignatureData getSignature() {
        return new SignatureData(get(ENTRY_SIGNATURE),
                get(ENTRY_SIG_HASH_CHAIN_RESULT),
                get(ENTRY_SIG_HASH_CHAIN));
    }

    public TimestampData getTimestamp() {
        if (entries.containsKey(ENTRY_TS_HASH_CHAIN_RESULT)
                && entries.containsKey(ENTRY_TS_HASH_CHAIN)) {
            return new TimestampData(get(ENTRY_TIMESTAMP),
                    get(ENTRY_TS_HASH_CHAIN_RESULT),
                    get(ENTRY_TS_HASH_CHAIN));
        }

        return null;
    }

    public String getManifest() {
        return get(ENTRY_MANIFEST);
    }

    public String getAsicManifest() {
        return get(ENTRY_ASIC_MANIFEST);
    }

    public byte[] getBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        write(out);

        return out.toByteArray();
    }

    public boolean isAttachment(String fileName) {
        return fileName.startsWith(ENTRY_ATTACHMENT);
    }

    public boolean hasEntry(String fileName) {
        String name = Helper.stripSlash(fileName);
        for (String entryName : entries.keySet()) {
            if (entryName.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    public InputStream getEntry(String fileName) {
        String data = get(Helper.stripSlash(fileName));
        return data != null ? new ByteArrayInputStream(
                data.getBytes(StandardCharsets.UTF_8)) : null;
    }

    public static AsicContainer read(InputStream is) throws Exception {
        return Helper.read(is);
    }

    public void write(OutputStream out) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            Helper.write(this, zip);
        }
    }

    private void createManifests() {
        createOpenDocumentManifest();
        // createAsicManifest();
    }

    private void createOpenDocumentManifest() {
        OpenDocumentManifestBuilder mb = new OpenDocumentManifestBuilder();
        for (String entryName : entries.keySet()) {
            // ignore mimetype and files in META-INF
            if (entryName.equalsIgnoreCase(ENTRY_MIMETYPE)
                    || entryName.startsWith("META-INF")) {
                continue;
            }

            mb.addFile(entryName, MimeTypes.TEXT_XML); // assume files are XML
        }

        put(ENTRY_MANIFEST, mb.build());
    }

    private void verifyContents() throws Exception {
        Helper.verifyMimeType(get(ENTRY_MIMETYPE));
        Helper.verifyMessage(get(ENTRY_MESSAGE));
        Helper.verifySignature(get(ENTRY_SIGNATURE),
                get(ENTRY_SIG_HASH_CHAIN_RESULT), get(ENTRY_SIG_HASH_CHAIN));
        Helper.verifyTimestamp(get(ENTRY_TIMESTAMP),
                get(ENTRY_TS_HASH_CHAIN_RESULT), get(ENTRY_TS_HASH_CHAIN));
        Helper.verifyManifest(get(ENTRY_MANIFEST), get(ENTRY_ASIC_MANIFEST));
    }

    String get(String entryName) {
        return entries.get(entryName);
    }

    void put(String entryName, String data) {
        if (isNotBlank(data)) {
            this.entries.put(entryName, data);
        }
    }
}
