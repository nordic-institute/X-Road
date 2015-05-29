package ee.ria.xroad_legacy.common.util;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.commons.io.output.WriterOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Manages passwords stored in the shared memory segment.
 */
public class PasswordStore {

    static {
        System.loadLibrary("passwordstore");
    }

    /**
     * Returns stored password with identifier id.
     * @return password value or null, if password with this ID was not found.
     */
    public static char[] getPassword(String id) throws Exception {
        byte[] raw = read(getPathnameForFtok(), id);
        return raw == null ? null : byteToChar(raw);
    }

    /** Stores the password in shared memory.
     * Use null as password parameter to remove password from memory.
     */
    public static void storePassword(String id, char[] password)
            throws Exception {
        byte[] raw = charToByte(password);
        write(getPathnameForFtok(), id, raw, 0600);
    }

    /** Clears the password store. Useful for testing purposes. */
    public static void clearStore() throws Exception {
        clear(getPathnameForFtok(), 0600);
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

    private static native byte[] read(String pathnameForFtok, String id)
            throws Exception;

    private static native void write(String pathnameForFtok,
            String id, byte[] password, int permissions) throws Exception;

    private static native void clear(String pathnameForFtok, int permissions)
            throws Exception;

    private static String getPathnameForFtok() {
        // Since we only plan to have one password store, we just use
        // root directory as identifier and hope that the project_id
        // part of the key are enough to separate us from the other
        // memory-accessing programs.
        return "/";
    }
}
