/**
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
package ee.ria.xroad.common.conf.globalconf;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Holds the list of recently downloaded files. Used for checking for removed
 * files in the downloaded configuration directory.
 */
@Slf4j
class DownloadedFiles {
    @Getter
    private final Set<String> downloadedFileList = new HashSet<>();

    private final Path confDir;
    private final Path confFile;

    DownloadedFiles(String confDir) {
        this.confDir = Paths.get(confDir);

        confFile = Paths.get(confDir, ConfigurationDirectory.FILES);
    }

    void reset() {
        downloadedFileList.clear();
    }

    void add(Set<String> files) {
        log.trace("add({})", files);

        downloadedFileList.addAll(files);
    }

    void sync() throws Exception {
        log.debug("sync({})", downloadedFileList);

        try (Stream<Path> paths = excludeMetadataAndDirs(Files.walk(confDir))) {
            paths.filter(f -> !downloadedFileList.contains(f.toString()))
                    .forEach(this::delete);
        }

        save();
    }

    private static Stream<Path> excludeMetadataAndDirs(Stream<Path> stream) {
        return stream.filter(Files::isRegularFile)
                .filter(p -> !p.endsWith(ConfigurationDirectory.FILES))
                .filter(p -> !p.endsWith(ConfigurationDirectory.INSTANCE_IDENTIFIER_FILE))
                .filter(p -> !p.toString().endsWith(ConfigurationDirectory.METADATA_SUFFIX));
    }

    void delete(Path path) {
        log.trace("delete({})", path);

        ConfigurationDirectory.delete(path.toString());
    }

    void save() throws Exception {
        log.trace("save()");

        FileUtils.writeLines(confFile.toFile(), StandardCharsets.UTF_8.name(), downloadedFileList);
    }
}
