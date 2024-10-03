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
package ee.ria.xroad.common.conf.globalconf;

import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.niis.xroad.confclient.proto.GetGlobalConfResp;
import org.niis.xroad.confclient.proto.GlobalConfFile;
import org.niis.xroad.confclient.proto.GlobalConfInstance;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static ee.ria.xroad.common.conf.globalconf.VersionedConfigurationDirectory.getVersion;
import static java.nio.charset.StandardCharsets.UTF_8;

abstract class BaseRemoteGlobalConfTest {
    static final String PATH_GOOD_GLOBALCONF = "src/test/resources/globalconf_good_v4";
    static final String INSTANCE_IDENTIFIER = "EE";

    @SneakyThrows
    GetGlobalConfResp loadGlobalConf(String instanceIdentifier, String path, long dateRefreshed) {
        var builder = GetGlobalConfResp.newBuilder();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(path), Files::isDirectory)) {
            for (Path instanceDir : stream) {

                builder.addInstances(loadParameters(instanceDir));
            }
        }
        return builder
                .setInstanceIdentifier(instanceIdentifier)
                .setDateRefreshed(dateRefreshed)
                .build();
    }

    private GlobalConfInstance loadParameters(Path instanceDir) {
        var builder = GlobalConfInstance.newBuilder();

        processFile(builder, instanceDir, ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS);
        processFile(builder, instanceDir, ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS);

        Integer version = getVersion(Paths.get(instanceDir.toString(), ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS));
        return builder
                .setInstanceIdentifier(instanceDir.getFileName().toString())
                .setVersion(version == null ? 2 : version)
                .build();
    }

    @SneakyThrows
    private void processFile(GlobalConfInstance.Builder builder, Path instanceDir, String fileName) {
        Path paramPath = Paths.get(instanceDir.toString(), fileName);
        if (Files.exists(paramPath)) {
            builder.addFiles(GlobalConfFile.newBuilder()
                    .setName(fileName)
                    .setContent(FileUtils.readFileToString(paramPath.toFile(), UTF_8))
                    .setChecksum("checksum"));
        }
    }

}
