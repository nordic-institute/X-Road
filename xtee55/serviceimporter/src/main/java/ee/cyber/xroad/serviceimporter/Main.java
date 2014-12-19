package ee.cyber.xroad.serviceimporter;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.SystemPropertiesLoader;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.xroad.mediator.MediatorSystemProperties;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;
import static ee.cyber.sdsb.common.SystemProperties.CONF_FILE_SIGNER;
import static ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.cyber.xroad.mediator.MediatorSystemProperties.*;

/**
 * Conf related utils for 5.0->SDSB migration.
 */
public class Main {

    static {
        new SystemPropertiesLoader(MediatorSystemProperties.PREFIX) {
            @Override
            public void loadWithCommonAndLocal() {
                load(CONF_FILE_MEDIATOR_COMMON);
                load(CONF_FILE_SERVICE_IMPORTER);
                load(CONF_FILE_SERVICE_MEDIATOR);
            }
        };

        new SystemPropertiesLoader() { // default prefix
            @Override
            public void loadWithCommonAndLocal() {
                load(CONF_FILE_SIGNER);
            }
        };
    }

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final String LOCK_FILE = "serviceimporter.lock";

    public static void main(String[] args) throws Exception {
        int exitCode = 0;

        try {
            CommandLine commandLine = parseArgs(args);

            //Path globalConfPath = Paths.get(SystemProperties.getGlobalConfFile());
            Path identifierMappingPath = Paths.get(
                    MediatorSystemProperties.getIdentifierMappingFile());

            String[] delete = commandLine.getOptionValues("delete");

            LOG.debug("Delete client/org = {}", Arrays.toString(delete));

            int optionsLength = commandLine.getOptions().length;

            if (commandLine.hasOption("import") || optionsLength == 0
                || (delete != null && optionsLength == 1)) {

                if (/*!requireFile(globalConfPath) ||*/ // TODO: globalconf check
                    !requireFile(identifierMappingPath)) {
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
                    if (delete.length != 4) {
                        throw new RuntimeException(
                            "Invalid value for -delete option");
                    }

                    deleteClientId = ClientId.create(
                            decodeBase64(delete[0]),
                            decodeBase64(delete[1]),
                            decodeBase64(delete[2]),
                            StringUtils.isBlank(delete[3])
                                    ? null : decodeBase64(delete[3]));
                }

                doExport(deleteClientId);
            }

            if (commandLine.hasOption("checksdsb")) {
                if (!new SDSBChecker().canActivate()) {
                    exitCode = 1;
                }
            }

            if (commandLine.hasOption("checkpromote")) {
                if (!new SDSBChecker().canPromote()) {
                    exitCode = 1;
                }
            }

        } catch (Throwable e) {
            LOG.error("ServiceImporter failed", e);
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

        LOG.warn("SDSB not configured (missing '{}')", path.toString());
        return false;
    }

    private static CommandLine parseArgs(String[] args) throws ParseException {
        BasicParser optionsParser = new BasicParser();
        Options options = new Options();

        OptionGroup mainOperations = new OptionGroup();
        mainOperations.addOption(
            new Option("import", "import services conf from 5.0 to SDSB"));
        mainOperations.addOption(
            new Option("export", "export services conf from SDSB to 5.0"));
        mainOperations.addOption(
            new Option("checksdsb", "check if SDSB can be activated"));
        mainOperations.addOption(
                new Option("checkpromote", "check if SDSB can be promoted"));

        // -delete option has the value of either
        // 'sdsbInstance,memberClass,memberCode,subsystemCode' or 'shortName'
        Option deleteOption =
            new Option("delete", true, "delete specified client/org");
        deleteOption.setRequired(false);
        deleteOption.setValueSeparator(',');
        deleteOption.setArgs(4);

        options.addOptionGroup(mainOperations);
        options.addOption(deleteOption);

        return optionsParser.parse(options, args);
    }
}
