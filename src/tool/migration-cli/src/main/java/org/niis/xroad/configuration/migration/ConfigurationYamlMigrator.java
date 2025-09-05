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
package org.niis.xroad.configuration.migration;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Map;

@Slf4j
class ConfigurationYamlMigrator {
    static final String PREFIX = "xroad";

    private final IniUtil iniUtil = new IniUtil();

    boolean migrate(String inputFilePath, String outputFilePath) throws IOException {
        var ini = new IniUtil().load(inputFilePath);

        Map<String, Object> properties = iniUtil.loadToNestedMap(inputFilePath, PREFIX);

        saveYamlToFile(ini, properties, outputFilePath);
        return true;
    }

    public static void saveYamlToFile(IniUtil.LoadedIniFile loadedIniFile,
                                      final Map<String, Object> properties,
                                      String outputPath) throws IOException {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setProcessComments(true);
        final Yaml yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("# Generated at: " + ZonedDateTime.now() + "\n");
            writer.write("# Input source: " + loadedIniFile.path().toAbsolutePath() + "\n");
            writer.write("# Original configuration:\n");
            writer.write("###########################\n");
            for (String line : loadedIniFile.rawContentLines()) {
                writer.write("# " + line + "\n");
            }

            writer.write("###########################\n\n");
            yaml.dump(properties, writer);
        }
    }

}
