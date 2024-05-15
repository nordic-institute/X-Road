package ee.ria.xroad.common.conf.globalconf;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.time.OffsetDateTime;
import java.util.Map;

final class ParametersProviderFactory {
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


    SharedParametersProvider sharedParametersProvider(byte[] content) throws CertificateEncodingException, IOException {
        return paramsConstructors.sharedByContent.create(content);
    }

    SharedParametersProvider sharedParametersProvider(Path sharedParametersPath, OffsetDateTime expiresOn) throws CertificateEncodingException, IOException {
        return paramsConstructors.sharedByPath.create(sharedParametersPath, expiresOn);
    }

    PrivateParametersProvider privateParametersProvider(byte[] content) throws CertificateEncodingException, IOException {
        return paramsConstructors.privateByContent.create(content);
    }

    PrivateParametersProvider privateParametersProvider(Path privateParametersPath, OffsetDateTime expiresOn) throws CertificateEncodingException, IOException {
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
