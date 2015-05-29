package ee.cyber.xroad.serviceimporter;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import ee.cyber.xroad.mediator.IdentifierMapping;
import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.db.HibernateUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import static ee.cyber.xroad.mediator.MediatorSystemProperties.*;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_SIGNER;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;

/**
 * Conf related utils for 5.0->6.0 migration.
 */
@Slf4j
public final class Main {

    private static final int DELETE_OPTION_LENGTH = 4;

    static {
        SystemPropertiesLoader.create(MediatorSystemProperties.PREFIX)
            .withLocal()
            .with(CONF_FILE_MEDIATOR_COMMON)
            .with(CONF_FILE_CLIENT_MEDIATOR)
            .with(CONF_FILE_SERVICE_MEDIATOR)
            .load();

        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_SIGNER)
            .load();
    }

    private static final String LOCK_FILE = "serviceimporter.lock";

    private Main() {
    }

    /**
     * Main program access point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        int exitCode = 0;

        try {
            CommandLine commandLine = parseArgs(args);

            Path globalConfPath = Paths.get(SystemProperties.getConfigurationAnchorFile());
            Path identifierMappingPath = Paths.get(
                    IdentifierMapping.getIdentifierMappingFile());

            String[] delete = commandLine.getOptionValues("delete");

            log.debug("Delete client/org = {}", Arrays.toString(delete));

            int optionsLength = commandLine.getOptions().length;

            if (commandLine.hasOption("import") || optionsLength == 0
                || (delete != null && optionsLength == 1)) {

                if (!requireFile(globalConfPath)
                        || !requireFile(identifierMappingPath)) {
                    return;
                }

                String deleteShortName = null;
                if (delete != null) {
                    if (delete.length != 1) {
                        throw new RuntimeException(
                            "Invalid value for -delete option");
                    }

                    deleteShortName = delete[0];
                }

                doImport(deleteShortName);
            }

            if (commandLine.hasOption("export")) {
                if (!requireFile(identifierMappingPath)) {
                    return;
                }

                ClientId deleteClientId = null;
                if (delete != null) {
                    if (delete.length != DELETE_OPTION_LENGTH) {
                        throw new RuntimeException(
                            "Invalid value for -delete option");
                    }

                    int idx = 0;
                    deleteClientId = ClientId.create(
                            decodeBase64(delete[idx++]),
                            decodeBase64(delete[idx++]),
                            decodeBase64(delete[idx++]),
                            StringUtils.isBlank(delete[idx])
                                    ? null : decodeBase64(delete[idx]));
                }

                doExport(deleteClientId);
            }

            if (commandLine.hasOption("checkxroad")) {
                if (!new XROADChecker().canActivate()) {
                    exitCode = 1;
                }
            }

            if (commandLine.hasOption("checkpromote")) {
                if (!new XROADChecker().canPromote()) {
                    exitCode = 1;
                }
            }

        } catch (Throwable e) {
            log.error("ServiceImporter failed", e);
            System.err.println("ServiceImporter failed: " + e.getMessage());

            exitCode = 2;
        } finally {
            HibernateUtil.closeSessionFactories();
        }

        System.exit(exitCode);
    }

    private static String decodeBase64(String str) {
        return new String(CryptoUtils.decodeBase64(str), StandardCharsets.UTF_8);
    }

    private static void doImport(final String deleteShortName)
            throws Exception {

        String lockFilePath = SystemProperties.getTempFilesPath() + LOCK_FILE;

        try (RandomAccessFile lockFile =
                 new RandomAccessFile(lockFilePath, "rw")) {

            // lock is released when lockFile is closed
            lockFile.getChannel().lock();

            doInTransaction(session -> {
                try {
                    new ServiceImporter().doImport(deleteShortName);
                    return null;
                } catch (Exception e) {
                    throw translateException(e);
                }
            });
        }
    }

    private static void doExport(final ClientId deleteClientId)
            throws Exception {
        doInTransaction(session -> {
            try {
                new ServiceImporter().doExport(deleteClientId);
                return null;
            } catch (Exception e) {
                throw translateException(e);
            }
        });
    }

    private static boolean requireFile(Path path) {
        if (Files.exists(path)) {
            return true;
        }

        log.warn("X-Road 6.0 not configured (missing '{}')", path.toString());
        return false;
    }

    private static CommandLine parseArgs(String[] args) throws ParseException {
        BasicParser optionsParser = new BasicParser();
        Options options = new Options();

        OptionGroup mainOperations = new OptionGroup();
        mainOperations.addOption(
            new Option("import", "import services conf from 5.0 to 6.0"));
        mainOperations.addOption(
            new Option("export", "export services conf from 6.0 to 5.0"));
        mainOperations.addOption(
            new Option("checkxroad", "check if X-Road 6.0 can be activated"));
        mainOperations.addOption(
                new Option("checkpromote", "check if X-Road 6.0 can be promoted"));

        // -delete option has the value of either
        // 'xRoadInstance,memberClass,memberCode,subsystemCode' or 'shortName'
        Option deleteOption =
            new Option("delete", true, "delete specified client/org");
        deleteOption.setRequired(false);
        deleteOption.setValueSeparator(',');
        deleteOption.setArgs(DELETE_OPTION_LENGTH);

        options.addOptionGroup(mainOperations);
        options.addOption(deleteOption);

        return optionsParser.parse(options, args);
    }
}
