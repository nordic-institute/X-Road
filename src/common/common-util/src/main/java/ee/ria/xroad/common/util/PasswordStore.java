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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.WriterOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Manages passwords that are shared across different JVMs.
 */
@Slf4j
@SuppressWarnings("squid:S2068")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PasswordStore {
    private static final String CFG_PASSWORD_STORE_PROVIDER = SystemProperties.PREFIX + "internal.passwordstore-provider";
    private static final String CFG_PASSWORD_STORE_FILE = "file";
    private static final int PERMISSIONS = 0600;

    private static final PasswordStoreProvider PASSWORD_STORE_PROVIDER;

    static {
        if (isFilePasswordStoreEnabled()) {
            log.warn("WARNING: FilePasswordStoreProvider is enabled. This provider is not production ready.");
            PASSWORD_STORE_PROVIDER = new FilePasswordStoreProvider();
        } else {
            PASSWORD_STORE_PROVIDER = new MemoryPasswordStoreProvider();
        }
    }

    private static boolean isFilePasswordStoreEnabled() {
        return CFG_PASSWORD_STORE_FILE.equals(System.getProperty(CFG_PASSWORD_STORE_PROVIDER));
    }

    /**
     * Returns stored password with identifier id.
     *
     * @param id identifier of the password
     * @return password value or null, if password with this ID was not found.
     * @throws Exception in case of any errors
     */
    public static char[] getPassword(String id) throws Exception {
        byte[] raw = PASSWORD_STORE_PROVIDER.read(getPathnameForFtok(), id);
        return raw == null ? null : byteToChar(raw);
    }

    /**
     * Stores the password in shared memory.
     * Use null as password parameter to remove password from memory.
     *
     * @param id       identifier of the password
     * @param password password to be stored
     * @throws Exception in case of any errors
     */
    public static void storePassword(String id, char[] password)
            throws Exception {
        byte[] raw = charToByte(password);
        PASSWORD_STORE_PROVIDER.write(getPathnameForFtok(), id, raw, PERMISSIONS);
    }

    /**
     * Clears the password store. Useful for testing purposes.
     *
     * @throws Exception in case of any errors
     */
    public static void clearStore() throws Exception {
        PASSWORD_STORE_PROVIDER.clear(getPathnameForFtok(), PERMISSIONS);
    }

    private static byte[] charToByte(char[] buffer) throws IOException {
        if (buffer == null) {
            return null;
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream(buffer.length * 2);
        OutputStreamWriter writer = new OutputStreamWriter(os, UTF_8);
        writer.write(buffer);
        writer.close();
        return os.toByteArray();
    }

    private static char[] byteToChar(byte[] bytes) throws IOException {
        if (bytes == null) {
            return null;
        }

        CharArrayWriter writer = new CharArrayWriter(bytes.length);
        WriterOutputStream os = new WriterOutputStream(writer, UTF_8);
        os.write(bytes);
        os.close();

        return writer.toCharArray();
    }

    private static String getPathnameForFtok() {
        return SystemProperties.getSignerPasswordStoreIPCKeyPathname();
    }

    public interface PasswordStoreProvider {
        byte[] read(String pathnameForFtok, String id) throws Exception;

        void write(String pathnameForFtok, String id, byte[] password, int permissions) throws Exception;

        void clear(String pathnameForFtok, int permissions) throws Exception;
    }
}
