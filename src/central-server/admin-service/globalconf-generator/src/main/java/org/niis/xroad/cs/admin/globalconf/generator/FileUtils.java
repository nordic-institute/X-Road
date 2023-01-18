/*
 * The MIT License
 *
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
package org.niis.xroad.cs.admin.globalconf.generator;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermissions.asFileAttribute;

@Slf4j
final class FileUtils {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwxr-xr-x");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-r--r--");

    private FileUtils() {
    }

    public static void createDirectories(Path dir) throws IOException {
        if (Files.exists(dir)) {
            return;
        }
        var parent = dir.getParent();
        if (parent != null && !Files.exists(parent)) {
            createDirectories(parent);
        }
        createDirectory(dir);
    }

    public static void createDirectory(Path dir) throws IOException {
        log.trace("createDirectory({})", dir);
        Files.createDirectory(dir, asFileAttribute(DIRECTORY_PERMISSIONS));
        // explicitly setting permissions, because attributes have no effect when process is running under umask
        Files.setPosixFilePermissions(dir, DIRECTORY_PERMISSIONS);
    }

    static void write(Path path, byte[] data) throws IOException {
        Files.write(path, data);
        Files.setPosixFilePermissions(path, FILE_PERMISSIONS);
    }

    static void writeString(Path path, String data) throws IOException {
        Files.writeString(path, data);
        Files.setPosixFilePermissions(path, FILE_PERMISSIONS);
    }
}
