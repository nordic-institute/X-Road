package ee.cyber.xroad.common.util;

import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import ee.cyber.xroad.common.DefaultFilepaths;

import static java.nio.file.StandardOpenOption.*;

public final class AtomicSave {

    public interface Callback {
        void save(OutputStream out) throws Exception;
    }

    public static void execute(String fileName, String tmpPrefix,
            Callback callback) throws Exception {
        Path tempFile = DefaultFilepaths.createTempFile(tmpPrefix, null);

        SeekableByteChannel channel = Files.newByteChannel(tempFile, CREATE,
                WRITE, TRUNCATE_EXISTING);

        try (OutputStream out = Channels.newOutputStream(channel)) {
            callback.save(out);
        }

        Path target = Paths.get(fileName);
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
