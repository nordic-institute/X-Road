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

import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationDirectory.PRIVATE_PARAMETERS_XML;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationDirectory.SHARED_PARAMETERS_XML;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationUtils.escapeInstanceIdentifier;

/**
 * Default implementation of file name provider.
 */
@RequiredArgsConstructor
public class FileNameProviderImpl implements FileNameProvider {

    private final String globalConfigurationDirectory;

    @Override
    public Path getFileName(ConfigurationFile file) throws Exception {
        String fileName;
        switch (file.getContentIdentifier()) {
            case PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS:
                fileName = PRIVATE_PARAMETERS_XML;
                break;
            case SharedParameters.CONTENT_ID_SHARED_PARAMETERS:
                fileName = SHARED_PARAMETERS_XML;
                break;
            default:
                fileName = Paths.get(
                    !StringUtils.isBlank(file.getContentFileName())
                        ? file.getContentFileName()
                        : file.getContentLocation()).getFileName().toString();
                break;
        }

        return Paths.get(globalConfigurationDirectory,
                escapeInstanceIdentifier(file.getInstanceIdentifier()),
                fileName);
    }
}
