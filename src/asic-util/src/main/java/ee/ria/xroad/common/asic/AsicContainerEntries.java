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

import ee.ria.xroad.common.util.MessageFileNames;

import lombok.Getter;

import java.util.regex.Pattern;

import static ee.ria.xroad.common.asic.AsicHelper.stripSlash;

/**
 * ASiC container entry name constants.
 */
public final class AsicContainerEntries {

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
            stripSlash(MessageFileNames.SIG_HASH_CHAIN_RESULT);

    /** The name suffix of the signature hash chain entry. */
    public static final String ENTRY_SIG_HASH_CHAIN =
            stripSlash(MessageFileNames.SIG_HASH_CHAIN);

    /** The name suffix of the time-stamp hash chain result entry. */
    public static final String ENTRY_TS_HASH_CHAIN_RESULT =
            stripSlash(MessageFileNames.TS_HASH_CHAIN_RESULT);

    /** The name suffix of the time-stamp hash chain entry. */
    public static final String ENTRY_TS_HASH_CHAIN =
            stripSlash(MessageFileNames.TS_HASH_CHAIN);

    /** The part of the name of the attachment entry. */
    public static final String ENTRY_ATTACHMENT = "attachment";

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

    @Getter
    private static final Object[] ALL_ENTRIES = {
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

    private AsicContainerEntries() {
    }

}
