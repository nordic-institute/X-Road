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

import ee.ria.xroad.common.DefaultFilepaths;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.DSYNC;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Holds atomic save utility methods.
 */
@Slf4j
public final class AtomicSave {

    private AtomicSave() {
    }

    /**
     * Functional interface for a callback that should be executed when data is being saved.
     */
    @FunctionalInterface
    public interface Callback {
        /**
         * Called when data is being atomically saved.
         * @param out output stream where data is written during the atomic save
         * @throws Exception if any errors occur
         */
        void save(OutputStream out) throws Exception;
    }

    /**
     * Atomically executes the given callback as part of the atomic save to the provided filename.
     * If an error occurs no changes to the file will be made.
     * @param fileName filename where data should be atomically saved
     * @param tmpPrefix prefix of the temporary file used in the process
     * @param callback callback that should be executed when data is atomically saved
     * @param options options specifying how the move should be done
     * @throws Exception if any errors occur
     */
    public static void execute(String fileName, String tmpPrefix, Callback callback, CopyOption... options)
            throws Exception {
        Path target = Paths.get(fileName);
        Path parentPath = target.getParent();
        Path tempFile = DefaultFilepaths.createTempFile(parentPath, tmpPrefix, null);

        try {
            SeekableByteChannel channel = Files.newByteChannel(tempFile, CREATE, WRITE, TRUNCATE_EXISTING, DSYNC);

            try (OutputStream out = Channels.newOutputStream(channel)) {
                callback.save(out);
            }

            if (options.length == 0) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(tempFile, target, options);
            }
        } finally {
            if (Files.exists(tempFile)) {
                Files.delete(tempFile);
            }
        }
    }

    /**
     * Atomically writes the given byte array to the file with the provided filename.
     * If an error occurs no changes to the file will be made.
     * @param fileName filename where data should be atomically saved
     * @param tmpPrefix prefix of the temporary file used in the process
     * @param data byte array that should be atomically saved in the file
     * @param options options specifying how the move should be done
     * @throws Exception if any errors occur
     */
    public static void execute(String fileName, String tmpPrefix, final byte[] data, CopyOption... options)
            throws Exception {
        execute(fileName, tmpPrefix, out -> out.write(data), options);
    }

    /**
     * Atomically moves one file from source path to target. Works between filesystems.
     * 1. copies file from source to target directory, with temp filename
     * 2. removes file from source
     * 3. renames file in target directory to target name
     * The last step is the part which is done atomically, it is still possible for example that
     * step 1 succeeds but step 2 fails
     * @param sourceFileName source
     * @param targetFileName target
     * @throws IOException when file operation fails
     */
    public static void moveBetweenFilesystems(String sourceFileName, String targetFileName) throws IOException {
        Path source = Paths.get(sourceFileName);
        Path target = Paths.get(targetFileName);
        Path tempFile = DefaultFilepaths.createTempFileInSameDir(targetFileName);

        try {
            Files.copy(source, tempFile, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(source);
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            if (Files.exists(tempFile)) {
                Files.delete(tempFile);
            }
        }
    }
}
