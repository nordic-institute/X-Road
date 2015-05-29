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
