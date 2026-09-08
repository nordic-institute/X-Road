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

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.signature.Signature;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.DigestCalculator;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

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
import static ee.ria.xroad.common.asic.AsicContainerEntries.isAttachment;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.niis.xroad.common.core.exception.ErrorCode.ASIC_HASH_CHAIN_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.ASIC_HASH_CHAIN_RESULT_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.ASIC_INVALID_MIME_TYPE;
import static org.niis.xroad.common.core.exception.ErrorCode.ASIC_MANIFEST_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.ASIC_MESSAGE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.ASIC_MIME_TYPE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.ASIC_SIGNATURE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.ASIC_TIMESTAMP_NOT_FOUND;

/**
 * Utility methods for dealing wit ASiC containers.
 */
final class AsicHelper {

    private AsicHelper() {
    }

    static AsicContainer read(InputStream is) throws IOException {
        Map<String, String> entries = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(is);
        ZipEntry zipEntry;
        Map<String, byte[]> attachmentDigests = new HashMap<>();

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
                } else if (isAttachment(zipEntry.getName())) {
                    final DigestCalculator digest =
                            Digests.createDigestCalculator(Digests.DEFAULT_DIGEST_ALGORITHM);
                    IOUtils.copy(zip, digest.getOutputStream());
                    attachmentDigests.put(zipEntry.getName(), digest.getDigest());
                    break;
                }
            }
        }

        return new AsicContainer(entries, attachmentDigests);
    }

    static void write(AsicContainer asic, ZipOutputStream zip) throws IOException {
        zip.setComment("mimetype=" + MIMETYPE);
        final long time = asic.getCreationTime();

        for (Object expectedEntry : AsicContainerEntries.getALL_ENTRIES()) {
            String name;

            if (expectedEntry instanceof String strEntry) {
                name = strEntry;
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

        if (asic.getAttachments() != null) {
            for (int i = 0; i < asic.getAttachments().size(); i++) {
                try (InputStream is = asic.getAttachments().get(i)) {
                    final ZipEntry e = new ZipEntry(ENTRY_ATTACHMENT + (i + 1));
                    e.setLastModifiedTime(FileTime.from(time, TimeUnit.MILLISECONDS));
                    zip.putNextEntry(e);
                    IOUtils.copy(is, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private static boolean matches(Object expectedEntry, String name) {
        if (expectedEntry instanceof String str) {
            return str.equalsIgnoreCase(name);
        } else if (expectedEntry instanceof Pattern pattern) {
            return pattern.matcher(name).matches();
        }

        return false;
    }

    static void verifyMimeType(String mimeType) {
        if (isBlank(mimeType)) {
            throw fileEmptyException(ASIC_MIME_TYPE_NOT_FOUND, ENTRY_MIMETYPE);
        }

        if (!MIMETYPE.equalsIgnoreCase(mimeType)) {
            throw XrdRuntimeException.systemException(ASIC_INVALID_MIME_TYPE, "Invalid mime type: %s", mimeType);
        }
    }

    static void verifyMessage(String message) {
        if (isBlank(message)) {
            throw fileEmptyException(ASIC_MESSAGE_NOT_FOUND, ENTRY_MESSAGE);
        }
    }

    static void verifySignature(String signature, String hashChainResult, String hashChain) {
        if (isBlank(signature)) {
            throw fileEmptyException(ASIC_SIGNATURE_NOT_FOUND, ENTRY_SIGNATURE);
        }

        verifyHashChainEntries(ENTRY_SIG_HASH_CHAIN_RESULT, hashChainResult,
                ENTRY_SIG_HASH_CHAIN, hashChain);
    }

    static void verifyTimestamp(String timestamp, String hashChainResult, String hashChain) {
        if (isNotNullAndIsBlank(timestamp)) {
            throw fileEmptyException(ASIC_TIMESTAMP_NOT_FOUND, ENTRY_TIMESTAMP);
        }

        verifyHashChainEntries(ENTRY_TS_HASH_CHAIN_RESULT, hashChainResult,
                ENTRY_TS_HASH_CHAIN, hashChain);
    }

    static void verifyManifest(String manifest, String asicManifest) {
        if (isNotNullAndIsBlank(manifest)) {
            throw fileEmptyException(ASIC_MANIFEST_NOT_FOUND, ENTRY_MANIFEST);
        }

        if (isNotNullAndIsBlank(asicManifest)) {
            throw fileEmptyException(ASIC_MANIFEST_NOT_FOUND, ENTRY_ASIC_MANIFEST);
        }
    }

    private static void verifyHashChainEntries(String hashChainResultEntryName, String hashChainResult,
                                               String hashChainEntryName, String hashChain) {
        if (isBlank(hashChainResult) && isBlank(hashChain)) {
            return;
        }

        if (isBlank(hashChainResult)) {
            throw fileEmptyException(ASIC_HASH_CHAIN_RESULT_NOT_FOUND, hashChainResultEntryName);
        }

        if (isBlank(hashChain)) {
            throw fileEmptyException(ASIC_HASH_CHAIN_NOT_FOUND, hashChainEntryName);
        }
    }

    private static String getData(ZipInputStream zip) throws IOException {
        return IOUtils.toString(zip, StandardCharsets.UTF_8);
    }

    private static byte[] getBinaryData(ZipInputStream zip) throws IOException {
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

    static String readTimestampFromSignatureXml(String signatureXml) {
        Signature signature = new Signature(signatureXml);

        return signature.getSignatureTimestamp();
    }

    private static boolean isNotNullAndIsBlank(String string) {
        return string != null && isBlank(string);
    }

    private static XrdRuntimeException fileEmptyException(ErrorCode errorCode, String fileName) {
        throw XrdRuntimeException.systemException(errorCode, "%s not found or is empty", fileName);
    }
}
