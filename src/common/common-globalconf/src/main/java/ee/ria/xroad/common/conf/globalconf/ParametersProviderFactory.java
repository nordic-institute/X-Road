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

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.time.OffsetDateTime;
import java.util.Map;

public final class ParametersProviderFactory {
    public static final int DEFAULT_VERSION = 2;
    private static final Map<Integer, ParamsConstructors> PARAMS_CONSTRUCTORS_MAP = Map.of(
            2, new ParamsConstructors(
                    SharedParametersV2::new,
                    SharedParametersV2::new,
                    PrivateParametersV2::new,
                    PrivateParametersV2::new
            ),
            3, new ParamsConstructors(
                    SharedParametersV3::new,
                    SharedParametersV3::new,
                    PrivateParametersV3::new,
                    PrivateParametersV3::new
            ),
            4, new ParamsConstructors(
                    SharedParametersV4::new,
                    SharedParametersV4::new,
                    PrivateParametersV3::new, // Version 4 private parameters are the same as version 3
                    PrivateParametersV3::new
            )
    );

    private final ParamsConstructors paramsConstructors;

    private ParametersProviderFactory(int globalConfigurationVersion) {
        this.paramsConstructors = PARAMS_CONSTRUCTORS_MAP.get(globalConfigurationVersion);
        if (paramsConstructors == null) {
            throw new IllegalArgumentException("Unsupported version: " + globalConfigurationVersion);
        }
    }

    public static ParametersProviderFactory forGlobalConfVersion(String version) {
        return new ParametersProviderFactory(version != null ? Integer.parseInt(version) : DEFAULT_VERSION);
    }

    public static ParametersProviderFactory forGlobalConfVersion(Integer version) {
        return new ParametersProviderFactory(version != null ? version : DEFAULT_VERSION);
    }


    public SharedParametersProvider sharedParametersProvider(byte[] content) throws CertificateEncodingException, IOException {
        return paramsConstructors.sharedByContent.create(content);
    }

    public SharedParametersProvider sharedParametersProvider(Path sharedParametersPath, OffsetDateTime expiresOn)
            throws CertificateEncodingException, IOException {
        return paramsConstructors.sharedByPath.create(sharedParametersPath, expiresOn);
    }

    public PrivateParametersProvider privateParametersProvider(byte[] content)
            throws CertificateEncodingException, IOException {
        return paramsConstructors.privateByContent.create(content);
    }

    public PrivateParametersProvider privateParametersProvider(Path privateParametersPath, OffsetDateTime expiresOn)
            throws CertificateEncodingException, IOException {
        return paramsConstructors.privateByPath.create(privateParametersPath, expiresOn);
    }

    interface ByContent<T> {
        T create(byte[] content) throws CertificateEncodingException, IOException;
    }

    interface ByPathAndExpireDate<T> {
        T create(Path privateParametersPath, OffsetDateTime expiresOn) throws CertificateEncodingException, IOException;
    }

    record ParamsConstructors(ByContent<SharedParametersProvider> sharedByContent,
                              ByPathAndExpireDate<SharedParametersProvider> sharedByPath,
                              ByContent<PrivateParametersProvider> privateByContent,
                              ByPathAndExpireDate<PrivateParametersProvider> privateByPath) {
    }
}
