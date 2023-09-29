/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.asic;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.signature.Signature;
import ee.ria.xroad.common.util.CryptoUtils;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.DigestCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static ee.ria.xroad.common.ErrorCodes.X_ASIC_HASH_CHAIN_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_ASIC_HASH_CHAIN_RESULT_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_ASIC_INVALID_MIME_TYPE;
import static ee.ria.xroad.common.ErrorCodes.X_ASIC_MANIFEST_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_ASIC_MESSAGE_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_ASIC_MIME_TYPE_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_ASIC_SIGNATURE_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_ASIC_TIMESTAMP_NOT_FOUND;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_ASIC_MANIFEST;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_ATTACHMENT;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_MANIFEST;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_MESSAGE;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_MIMETYPE;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_SIGNATURE;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_SIG_HASH_CHAIN;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_SIG_HASH_CHAIN_RESULT;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_TIMESTAMP;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_TS_HASH_CHAIN;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_TS_HASH_CHAIN_RESULT;
import static ee.ria.xroad.common.asic.AsicContainerEntries.MIMETYPE;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Utility methods for dealing wit ASiC containers.
 */
final class AsicHelper {

    private AsicHelper() {
    }

    static AsicContainer read(InputStream is) throws Exception {
        Map<String, String> entries = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(is);
        ZipEntry zipEntry;
        byte[] attachmentDigest = null;

        while ((zipEntry = zip.getNextEntry()) != null) {
            for (Object expectedEntry : AsicContainerEntries.getALL_ENTRIES()) {
                if (matches(expectedEntry, zipEntry.getName())) {
                    String data;

                    if (ENTRY_TIMESTAMP.equalsIgnoreCase(zipEntry.getName())) {
                        data = encodeBase64(getBinaryData(zip));
                    } else {
                        data = getData(zip);
                    }

                    entries.put(zipEntry.getName(), data);

                    break;
                } else if (matches(ENTRY_ATTACHMENT + "1", zipEntry.getName())) {
                    final DigestCalculator digest =
                            CryptoUtils.createDigestCalculator(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);
                    IOUtils.copy(zip, digest.getOutputStream());
                    attachmentDigest = digest.getDigest();
                    break;
                }
            }
        }

        return new AsicContainer(entries, attachmentDigest);
    }

    static void write(AsicContainer asic, ZipOutputStream zip) throws Exception {
        zip.setComment("mimetype=" + MIMETYPE);
        final long time = asic.getCreationTime();

        for (Object expectedEntry : AsicContainerEntries.getALL_ENTRIES()) {
            String name;

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
                        addEntry(zip, name, time, binary);
                    }
                } else {
                    addEntry(zip, name, time, data);
                }
            }
        }

        if (asic.getAttachment() != null) {
            try (InputStream is = asic.getAttachment()) {
                final ZipEntry e = new ZipEntry(ENTRY_ATTACHMENT + "1");
                e.setLastModifiedTime(FileTime.from(time, TimeUnit.MILLISECONDS));
                zip.putNextEntry(e);
                IOUtils.copy(is, zip);
                zip.closeEntry();
            }
        }
    }

    private static boolean matches(Object expectedEntry, String name) {
        if (expectedEntry instanceof String) {
            return ((String) expectedEntry).equalsIgnoreCase(name);
        } else if (expectedEntry instanceof Pattern) {
            return ((Pattern) expectedEntry).matcher(name).matches();
        }

        return false;
    }

    static void verifyMimeType(String mimeType) throws Exception {
        if (isBlank(mimeType)) {
            throw fileEmptyException(X_ASIC_MIME_TYPE_NOT_FOUND, ENTRY_MIMETYPE);
        }

        if (!MIMETYPE.equalsIgnoreCase(mimeType)) {
            throw new CodedException(X_ASIC_INVALID_MIME_TYPE, "Invalid mime type: %s", mimeType);
        }
    }

    static void verifyMessage(String message) {
        if (isBlank(message)) {
            throw fileEmptyException(X_ASIC_MESSAGE_NOT_FOUND, ENTRY_MESSAGE);
        }
    }

    static void verifySignature(String signature, String hashChainResult, String hashChain) {
        if (isBlank(signature)) {
            throw fileEmptyException(X_ASIC_SIGNATURE_NOT_FOUND, ENTRY_SIGNATURE);
        }

        verifyHashChainEntries(ENTRY_SIG_HASH_CHAIN_RESULT, hashChainResult,
                ENTRY_SIG_HASH_CHAIN, hashChain);
    }

    static void verifyTimestamp(String timestamp, String hashChainResult, String hashChain) {
        if (isNotNullAndIsBlank(timestamp)) {
            throw fileEmptyException(X_ASIC_TIMESTAMP_NOT_FOUND, ENTRY_TIMESTAMP);
        }

        verifyHashChainEntries(ENTRY_TS_HASH_CHAIN_RESULT, hashChainResult,
                ENTRY_TS_HASH_CHAIN, hashChain);
    }

    static void verifyManifest(String manifest, String asicManifest) {
        if (isNotNullAndIsBlank(manifest)) {
            throw fileEmptyException(X_ASIC_MANIFEST_NOT_FOUND, ENTRY_MANIFEST);
        }

        if (isNotNullAndIsBlank(asicManifest)) {
            throw fileEmptyException(X_ASIC_MANIFEST_NOT_FOUND, ENTRY_ASIC_MANIFEST);
        }
    }

    private static void verifyHashChainEntries(String hashChainResultEntryName, String hashChainResult,
            String hashChainEntryName, String hashChain) {
        if (isBlank(hashChainResult) && isBlank(hashChain)) {
            return;
        }

        if (isBlank(hashChainResult)) {
            throw fileEmptyException(X_ASIC_HASH_CHAIN_RESULT_NOT_FOUND, hashChainResultEntryName);
        }

        if (isBlank(hashChain)) {
            throw fileEmptyException(X_ASIC_HASH_CHAIN_NOT_FOUND, hashChainEntryName);
        }
    }

    private static String getData(ZipInputStream zip) throws Exception {
        return IOUtils.toString(zip, StandardCharsets.UTF_8);
    }

    private static byte[] getBinaryData(ZipInputStream zip) throws Exception {
        return IOUtils.toByteArray(zip);
    }

    private static void addEntry(ZipOutputStream zip, String name, long time, String data) throws IOException {
        addEntry(zip, name, time, data.getBytes(StandardCharsets.UTF_8));
    }

    private static void addEntry(ZipOutputStream zip, String name, long time, byte[] data) throws IOException {
        final ZipEntry e = new ZipEntry(name);
        e.setLastModifiedTime(FileTime.from(time, TimeUnit.MILLISECONDS));
        zip.putNextEntry(e);
        zip.write(data);
    }

    static String stripSlash(String name) {
        if (name.startsWith("/")) {
            return name.substring(1);
        }

        return name;
    }

    static String readTimestampFromSignatureXml(String signatureXml) throws Exception {
        Signature signature = new Signature(signatureXml);

        return signature.getSignatureTimestamp();
    }

    private static boolean isNotNullAndIsBlank(String string) {
        return string != null && isBlank(string);
    }

    private static CodedException fileEmptyException(String errorCode, String fileName) {
        throw new CodedException(errorCode, "%s not found or is empty", fileName);
    }
}
