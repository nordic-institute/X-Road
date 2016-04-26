/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * Holds the list of recently downloaded files. Used for checking for removed
 * files in the downloaded configuration directory.
 */
@Slf4j
class DownloadedFiles {

    private final Set<String> oldList = new HashSet<>();
    private final Set<String> newList = new HashSet<>();

    private final Path confFile;

    DownloadedFiles(Path confFile) {
        this.confFile = confFile;
        try {
            load();
        } catch (Exception e) {
            log.error("Failed to load last list of downloaded files", e);
        }
    }

    void add(Set<String> files) {
        log.trace("add({})", files);

        newList.addAll(files);
    }

    void sync() throws Exception {
        log.trace("sync({})", newList);

        oldList.stream().filter(f -> !newList.contains(f))
            .forEach(this::delete);

        oldList.clear();
        oldList.addAll(newList);
        newList.clear();

        save();
    }

    void delete(String file) {
        log.trace("delete({})", file);

        ConfigurationDirectory.delete(file);
    }

    void load() throws Exception {
        log.trace("load()");

        File file = confFile.toFile();
        if (file.exists()) {
            oldList.addAll(FileUtils.readLines(file));
        }
    }

    void save() throws Exception {
        log.trace("save()");

        try (FileWriter file = new FileWriter(confFile.toFile())) {
            IOUtils.writeLines(oldList, null, file);
        }
    }
}
