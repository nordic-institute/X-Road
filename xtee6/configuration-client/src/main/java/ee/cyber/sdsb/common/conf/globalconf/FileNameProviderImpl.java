package ee.cyber.sdsb.common.conf.globalconf;

import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang.StringUtils;

import static ee.cyber.sdsb.common.conf.globalconf.ConfigurationDirectory.PRIVATE_PARAMETERS_XML;
import static ee.cyber.sdsb.common.conf.globalconf.ConfigurationDirectory.SHARED_PARAMETERS_XML;
import static ee.cyber.sdsb.common.conf.globalconf.ConfigurationUtils.escapeInstanceIdentifier;

/**
 * Default implementation of file name provider.
 */
@RequiredArgsConstructor
public class FileNameProviderImpl implements FileNameProvider {

    private final String globalConfigurationDirectory;
    private final String systemConfigurationDirectory;

    @Override
    public Path getFileName(ConfigurationFile file) throws Exception {
        String root = globalConfigurationDirectory;
        String instance =
                escapeInstanceIdentifier(file.getInstanceIdentifier());

        switch (file.getContentIdentifier()) {
            case PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS:
                return Paths.get(root, instance, PRIVATE_PARAMETERS_XML);
            case SharedParameters.CONTENT_ID_SHARED_PARAMETERS:
                return Paths.get(root, instance, SHARED_PARAMETERS_XML);
            default:
                Path fileName = Paths.get(
                    !StringUtils.isBlank(file.getContentFileName())
                        ? file.getContentFileName()
                        : file.getContentLocation());
                return Paths.get(systemConfigurationDirectory,
                        fileName.getFileName().toString());
        }
    }

}
