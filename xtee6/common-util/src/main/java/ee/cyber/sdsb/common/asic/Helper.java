package ee.cyber.sdsb.common.asic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.signature.Signature;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.asic.AsicContainerEntries.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.decodeBase64;
import static ee.cyber.sdsb.common.util.CryptoUtils.encodeBase64;
import static org.apache.commons.lang.StringUtils.isBlank;

class Helper {

    static AsicContainer read(InputStream is) throws Exception {
        Map<String, String> entries = new HashMap<>();

        ZipInputStream zip = new ZipInputStream(is);

        ZipEntry zipEntry;
        while ((zipEntry = zip.getNextEntry()) != null) {
            for (Object expectedEntry : AsicContainerEntries.ALL_ENTRIES) {
                if (matches(expectedEntry, zipEntry.getName())) {
                    String data;
                    if (ENTRY_TIMESTAMP.equalsIgnoreCase(zipEntry.getName())) {
                        data = encodeBase64(getBinaryData(zip));
                    } else {
                        data = getData(zip);
                    }

                    entries.put(zipEntry.getName(), data);
                    break;
                }
            }
        }

        return new AsicContainer(entries);
    }

    static void write(AsicContainer asic, ZipOutputStream zip)
            throws Exception {
        zip.setComment("mimetype=" + MIMETYPE);

        for (Object expectedEntry : AsicContainerEntries.ALL_ENTRIES) {
            String name = null;
            if (expectedEntry instanceof String) {
                name = (String) expectedEntry;
            } else if (expectedEntry instanceof Pattern) {
                name = ENTRY_SIGNATURE;
            } else {
                continue;
            }

            String data = asic.get(name);
            if (data != null) {
                if (ENTRY_TIMESTAMP.equalsIgnoreCase(name)) {
                    // If the timestamp is batch timestamp, add the timestamp.tst
                    // to the container, else the timestamp is in the signature
                    if (asic.getTimestamp() != null) {
                        byte[] binary = decodeBase64(data);
                        addEntry(zip, name, binary);
                    }
                } else {
                    addEntry(zip, name, data);
                }
            }
        }
    }

    static boolean matches(Object expectedEntry, String name) {
        if (expectedEntry instanceof String) {
            return ((String) expectedEntry).equalsIgnoreCase(name);
        } else if (expectedEntry instanceof Pattern) {
            return ((Pattern) expectedEntry).matcher(name).matches();
        }

        return false;
    }

    static void verifyMimeType(String mimeType) throws Exception {
        if (isBlank(mimeType)) {
            throw fileEmptyException(X_ASIC_MIME_TYPE_NOT_FOUND,
                    ENTRY_MIMETYPE);
        }

        if (!MIMETYPE.equalsIgnoreCase(mimeType)) {
            throw new CodedException(X_ASIC_INVALID_MIME_TYPE,
                    "Invalid mime type: %s", mimeType);
        }
    }

    static void verifyMessage(String message) {
        if (isBlank(message)) {
            throw fileEmptyException(X_ASIC_MESSAGE_NOT_FOUND, ENTRY_MESSAGE);
        }
    }

    static void verifySignature(String signature, String hashChainResult,
            String hashChain) {
        if (isBlank(signature)) {
            throw fileEmptyException(X_ASIC_SIGNATURE_NOT_FOUND,
                    ENTRY_SIGNATURE);
        }

        verifyHashChainEntries(ENTRY_SIG_HASH_CHAIN_RESULT, hashChainResult,
                ENTRY_SIG_HASH_CHAIN, hashChain);
    }

    static void verifyTimestamp(String timestamp, String hashChainResult,
            String hashChain) {
        if (isNotNullAndIsBlank(timestamp)) {
            throw fileEmptyException(X_ASIC_TIMESTAMP_NOT_FOUND,
                    ENTRY_TIMESTAMP);
        }

        verifyHashChainEntries(ENTRY_TS_HASH_CHAIN_RESULT, hashChainResult,
                ENTRY_TS_HASH_CHAIN, hashChain);
    }

    static void verifyManifest(String manifest, String asicManifest) {
        if (isNotNullAndIsBlank(manifest)) {
            throw fileEmptyException(X_ASIC_MANIFEST_NOT_FOUND,
                    ENTRY_MANIFEST);
        }

        if (isNotNullAndIsBlank(asicManifest)) {
            throw fileEmptyException(X_ASIC_MANIFEST_NOT_FOUND,
                    ENTRY_ASIC_MANIFEST);
        }
    }

    static void verifyHashChainEntries(String hashChainResultEntryName,
            String hashChainResult, String hashChainEntryName,
            String hashChain) {
        if (isBlank(hashChainResult) && isBlank(hashChain)) {
            return;
        }

        if (isBlank(hashChainResult)) {
            throw fileEmptyException(X_ASIC_HASH_CHAIN_RESULT_NOT_FOUND,
                    hashChainResultEntryName);
        }

        if (isBlank(hashChain)) {
            throw fileEmptyException(X_ASIC_HASH_CHAIN_NOT_FOUND,
                    hashChainEntryName);
        }
    }

    static String getData(ZipInputStream zip) throws Exception {
        return IOUtils.toString(zip, StandardCharsets.UTF_8);
    }

    static byte[] getBinaryData(ZipInputStream zip) throws Exception {
        return IOUtils.toByteArray(zip);
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

    static String stripSlash(String name) {
        if (name.startsWith("/")) {
            return name.substring(1);
        }

        return name;
    }

    static String readTimestampFromSignatureXml(String signatureXml)
            throws Exception {
        Signature signature = new Signature(signatureXml);
        return signature.getSignatureTimestamp();
    }

    private static boolean isNotNullAndIsBlank(String string) {
        return string != null && isBlank(string);
    }

    private static CodedException fileEmptyException(String errorCode,
            String fileName) {
        throw new CodedException(errorCode, "%s not found or is empty",
                fileName);
    }
}
