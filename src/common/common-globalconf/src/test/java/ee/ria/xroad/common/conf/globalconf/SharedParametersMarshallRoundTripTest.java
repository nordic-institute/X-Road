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
package ee.ria.xroad.common.conf.globalconf;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class SharedParametersMarshallRoundTripTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "src/test/resources/globalconf_good_v4/EE/shared-params.xml",
            "src/test/resources/globalconf_good_v3/EE/shared-params.xml",
            "src/test/resources/globalconf_good_v2/EE/shared-params.xml"
    })
    void shouldEqualAfterXmlMarshalling(String sharedParamsPath) throws Exception {
        var path = Paths.get(sharedParamsPath);
        var version = VersionedConfigurationDirectory.getVersion(path);
        var content = FileUtils.readFileToByteArray(path.toFile());
        var parametersProviderFactory = ParametersProviderFactory.forGlobalConfVersion(version);
        var sharedParametersProvider = parametersProviderFactory.sharedParametersProvider(content);

        var initial = sharedParametersProvider.getSharedParameters();
        var xml = sharedParametersProvider.getMarshaller().marshall(initial);
        var afterMarshalling = parametersProviderFactory.sharedParametersProvider(xml.getBytes(UTF_8)).getSharedParameters();

        assertThat(initial)
                .usingRecursiveComparison()
                .isEqualTo(afterMarshalling);

    }


}
