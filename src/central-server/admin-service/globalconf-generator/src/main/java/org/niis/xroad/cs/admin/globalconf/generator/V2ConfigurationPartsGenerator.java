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

import ee.ria.xroad.common.conf.globalconf.ConfigurationConstants;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;
import static java.nio.charset.StandardCharsets.UTF_8;

@Component
@RequiredArgsConstructor
public class V2ConfigurationPartsGenerator implements ConfigurationPartsGenerator {

    private static final int CONFIGURATION_VERSION = 2;

    private final PrivateParametersV2Generator privateParametersV2Generator;
    private final SharedParametersV2Generator sharedParametersV2Generator;

    public int getConfigurationVersion() {
        return CONFIGURATION_VERSION;
    }

    public List<ConfigurationPart> generateConfigurationParts() {
        return List.of(
                ConfigurationPart.builder()
                        .contentIdentifier(ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS)
                        .filename(ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS)
                        .data(privateParametersV2Generator.generate().getBytes(UTF_8))
                        .build(),
                ConfigurationPart.builder()
                        .contentIdentifier(CONTENT_ID_SHARED_PARAMETERS)
                        .filename(ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS)
                        .data(sharedParametersV2Generator.generate().getBytes(UTF_8))
                        .build());
    }

}
