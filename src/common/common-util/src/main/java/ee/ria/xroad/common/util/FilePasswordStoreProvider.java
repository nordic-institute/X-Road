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

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.Arrays;

import java.io.File;

import static java.lang.String.format;

/**
 * A simplified password store implementation which uses files as storage medium.
 * This implementation is designed purely for testing purposes.
 */
@Slf4j
@SuppressWarnings("squid:S2068")
public class FilePasswordStoreProvider implements PasswordStore.PasswordStoreProvider {
    private static final String CFG_FILE_PASSWORD_STORE_PATH = SystemProperties.PREFIX + "internal.passwordstore-file-path";

    private static final String PATTERN_FILE_PASSWORDSTORE = "%s/.pswd-%s";

    @Override
    public synchronized byte[] read(String pathnameForFtok, String id) throws Exception {
        var file = getFileById(id);

        log.warn("Reading password from {}. File exists? {}", file, file.exists());
        if (file.exists()) {
            try {
                return FileUtils.readFileToByteArray(file);
            } catch (Exception e) {
                log.warn("Failed to read passwordstore from file", e);
            }
        }

        return null;
    }

    @Override
    public synchronized void write(String pathnameForFtok, String id, byte[] password, int permissions) throws Exception {
        var file = getFileById(id);

        log.warn("Writing password to {}", file);
        try {
            if (Arrays.isNullOrEmpty(password)) {
                FileUtils.delete(file);
            } else {
                FileUtils.writeByteArrayToFile(file, password, false);
            }
        } catch (Exception e) {
            log.warn("Failed to write to passwordstore", e);
        }
    }

    @Override
    public synchronized void clear(String pathnameForFtok, int permissions) throws Exception {
        //NO-OP
    }

    private File getFileById(String id) {
        return new File(format(PATTERN_FILE_PASSWORDSTORE, getPasswordStorePath(), id));
    }

    private String getPasswordStorePath() {
        return System.getProperty(CFG_FILE_PASSWORD_STORE_PATH, "/tmp/xroad/passwordstore/");
    }
}
