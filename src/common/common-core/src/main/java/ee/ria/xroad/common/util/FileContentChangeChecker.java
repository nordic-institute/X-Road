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
package ee.ria.xroad.common.util;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.ChangeChecker;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static ee.ria.xroad.common.crypto.Digests.hexDigest;
import static org.apache.commons.io.IOUtils.toByteArray;

/**
 * A checksum based file modification checker.
 */
@Slf4j
public class FileContentChangeChecker implements ChangeChecker {

    @Getter
    private final String fileName;

    @Getter
    private String checksum;
    private String previousChecksum;

    /**
     * Calculates hash of the input file.
     *
     * @param fileName the input file
     * @throws Exception if an error occurs
     */
    public FileContentChangeChecker(String fileName) throws IOException, OperatorCreationException {
        this.fileName = fileName;

        File file = getFile();
        this.checksum = calculateConfFileChecksum(file);
    }

    /**
     * @return true, if the file has changed
     * @throws Exception if an error occurs
     */
    @Override
    public boolean hasChanged() throws IOException, OperatorCreationException {
        File file = getFile();

        String newCheckSum = calculateConfFileChecksum(file);

        synchronized (this) {
            previousChecksum = checksum;
            checksum = newCheckSum;
            return !Objects.equals(checksum, previousChecksum);
        }
    }

    protected File getFile() {
        return new File(fileName);
    }

    protected InputStream getInputStream(File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }

    protected String calculateConfFileChecksum(File file) throws IOException, OperatorCreationException {
        if (!file.exists()) {
            log.warn("File {} does not exist", file);
            return null;
        }
        try (InputStream in = getInputStream(file)) {
            return hexDigest(DigestAlgorithm.MD5, toByteArray(in));
        }
    }
}
