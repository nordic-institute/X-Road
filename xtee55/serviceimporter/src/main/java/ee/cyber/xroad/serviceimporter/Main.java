package ee.cyber.xroad.serviceimporter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.ArrayUtils;

import ee.cyber.xroad.mediator.IdentifierMapping;
import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.db.HibernateUtil;

import static ee.cyber.xroad.mediator.MediatorSystemProperties.CONF_FILE_CLIENT_MEDIATOR;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_SIGNER;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;

/**
 * Conf related utils for 5.0->6.0 migration.
 */
@Slf4j
public final class Main {

    private static final int MIN_SERVICE_OPTION_PARTS = 2;
    private static final int MAX_SERVICE_OPTION_PARTS = 3;

    static {
        SystemPropertiesLoader.create(MediatorSystemProperties.PREFIX)
            .withLocal()
            .with(CONF_FILE_CLIENT_MEDIATOR)
            .load();

        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_SIGNER)
            .load();
    }

    private static final String LOCK_FILE = "serviceimporter.lock";

    private static ServiceImporter serviceImporter = new ServiceImporter();

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

            checkRequiredFile(SystemProperties.getConfigurationAnchorFile());
            checkRequiredFile(IdentifierMapping.getIdentifierMappingFile());

            if (commandLine.hasOption("noAcl")) {
                doImport(services());
            } else if (commandLine.hasOption("acl")) {
                doImport(acl());
            } else if (commandLine.hasOption("aclOfProducer")) {
                String producer = commandLine.getOptionValue("aclOfProducer");
                doImport(aclOfProducer(producer));
            } else if (commandLine.hasOption("aclOfService")) {
                // Producer short name is in format name[.subsystem]
                String[] parts =
                        commandLine.getOptionValue("aclOfService").split("\\.");

                if (parts.length < MIN_SERVICE_OPTION_PARTS
                        || parts.length > MAX_SERVICE_OPTION_PARTS) {
                    throw new IllegalArgumentException("Wrong service name format"
                            + " (" + commandLine.getOptionValue("aclOfService")
                            + "), should be <producer short name>.<service name>");
                }

                doImport(aclOfService(parts.length == MIN_SERVICE_OPTION_PARTS
                        ? parts[0] : parts[0] + "." + parts[1],
                        parts[parts.length - 1]));
            } else {
                doImport(all());
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

    private static ImporterAction[] all() throws IOException {
        return ArrayUtils.addAll(new ImporterAction[] {services()}, acl());
    }

    private static ImporterAction aclOfService(String producer, String service) {
        return () -> {
            doInTransaction(session -> {
                try {
                    serviceImporter.importAcl(producer, service);
                    return null;
                } catch (Exception e) {
                    throw translateException(e);
                }
            });
        };
    }

    private static ImporterAction[] aclOfProducer(String producer) {
        List<String> clients;

        try {
            clients = serviceImporter.getServiceCodesToImport(producer);
        } catch (Exception e) {
            log.error("Could not get a list of services of producer '{}'",
                    producer);

            return new ImporterAction[0];
        }

        if (clients == null) {
            System.err.println("Producer '" + producer
                    + "' does not exist in X-Road 5.0");

            return new ImporterAction[0];
        }

        List<ImporterAction> actions = clients.stream()
                .map(service -> aclOfService(producer, service))
                .collect(Collectors.toList());

        return actions.toArray(new ImporterAction[clients.size()]);
    }

    private static ImporterAction[] acl() throws IOException {
        List<String> producersNames = serviceImporter.getProducersNamesToImport();
        List<ImporterAction> actions = producersNames.stream()
                    .map(producer -> aclOfProducer(producer))
                    .flatMap(Arrays::stream)
                    .collect(Collectors.toList());

        return actions.toArray(new ImporterAction[producersNames.size()]);
    }

    private static ImporterAction services() {
        return () -> {
            doInTransaction(session -> {
                try {
                    serviceImporter.importServices();
                    return null;
                } catch (Exception e) {
                    throw translateException(e);
                }
            });
        };
    }

    private static void doImport(ImporterAction... actions) throws Exception {
        String lockFilePath = SystemProperties.getTempFilesPath() + LOCK_FILE;

        try (RandomAccessFile lockFile =
                 new RandomAccessFile(lockFilePath, "rw")) {
            // lock is released when lockFile is closed
            lockFile.getChannel().lock();

            for (ImporterAction action : actions) {
                action.run();
            }
        }
    }

    private static void checkRequiredFile(String path) {
        if (!Files.exists(Paths.get(path))) {
            throw new RuntimeException(String.format(
                    "X-Road 6.0 not configured (missing '%s')", path));
        }
    }

    private static CommandLine parseArgs(String[] args) throws ParseException {
        BasicParser optionsParser = new BasicParser();
        Options options = new Options();

        OptionGroup mainOperations = new OptionGroup();
        mainOperations.addOption(new Option("noAcl",
                "import services from 5.0 to 6.0 without ACLs"));
        mainOperations.addOption(new Option("acl",
                "import service ACLs from 5.0 to 6.0"));
        mainOperations.addOption(new Option("aclOfProducer", true,
                "import service ACL from 5.0 to 6.0 of the given producer"
                        + " (<producer short name>)"));
        mainOperations.addOption(new Option("aclOfService", true,
                "import service ACL from 5.0 to 6.0 of the given service"
                        + " (<producer short name>.<service name>)"));

        options.addOptionGroup(mainOperations);

        return optionsParser.parse(options, args);
    }

    private interface ImporterAction {
        void run() throws Exception;
    }
}
