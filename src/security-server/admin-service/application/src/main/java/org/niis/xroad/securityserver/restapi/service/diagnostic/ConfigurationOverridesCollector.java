/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.service.diagnostic;

import ee.ria.xroad.common.SystemProperties;

import lombok.RequiredArgsConstructor;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Order(DiagnosticCollector.ORDER_GROUP2)
public class ConfigurationOverridesCollector implements DiagnosticCollector<List<String>> {

    @Override
    public String name() {
        return "Configuration overrides from local.ini";
    }

    @Override
    public List<String> collect() {
        INIConfiguration ini = new INIConfiguration();
        // turn off list delimiting (before parsing),
        // otherwise we lose everything after first ","
        // in loadSection/sec.getString(key)
        ini.setListDelimiterHandler(DisabledListDelimiterHandler.INSTANCE);
        try (var r = Files.newBufferedReader(Paths.get(SystemProperties.CONF_FILE_USER_LOCAL))) {
            var keys = new LinkedList<String>();
            ini.read(r);

            for (String sectionName : ini.getSections()) {
                ini.getSection(sectionName).getKeys().forEachRemaining(key -> keys.add(sectionName + "." + key));
            }
            return keys;
        } catch (IOException | ConfigurationException e) {
            throw new RuntimeException("Failed to read local.ini file", e);
        }
    }
}
