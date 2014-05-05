package ee.cyber.xroad.serviceimporter;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.db.TransactionCallback;
import ee.cyber.xroad.mediator.MediatorSystemProperties;

/**
 * Conf related utils for 5.0->SDSB migration.
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final String LOCK_FILE = "serviceimporter.lock";

    public static void main(String[] args) throws Exception {
        try {
            CommandLine commandLine = parseArgs(args);

            Path globalConfPath = Paths.get(SystemProperties.getGlobalConfFile());
            Path identifierMappingPath =
                Paths.get(MediatorSystemProperties.getIdentifierMappingFile());

            if (commandLine.hasOption("import") ||
                commandLine.getOptions().length == 0) {

                if (!requireFile(globalConfPath) ||
                    !requireFile(identifierMappingPath)) {
                    return;
                }

                doImport();
            }

            if (commandLine.hasOption("export")) {
                if (!requireFile(identifierMappingPath)) {
                    return;
                }

                doExport();
            }

            if (commandLine.hasOption("checksdsb")) {
                if (new SDSBChecker().canActivate()) {
                    System.exit(0);
                } else {
                    System.exit(1);
                }
            }

        } catch (Exception ex) {
            LOG.error("ServiceImporter failed", ex);
            throw ex;
        } finally {
            HibernateUtil.closeSessionFactory();
        }
    }

    private static void doImport() throws Exception {
        String lockFilePath = SystemProperties.getTempFilesPath() + LOCK_FILE;

        try (RandomAccessFile lockFile =
                 new RandomAccessFile(lockFilePath, "rw")) {

            // lock is released when lockFile is closed
            lockFile.getChannel().lock();

            HibernateUtil.doInTransaction(new TransactionCallback<Object>() {
                @Override
                public Object call(Session session) throws Exception {
                    new ServiceImporter().doImport();
                    return null;
                }
            });
        }
    }

    private static void doExport() throws Exception {
        HibernateUtil.doInTransaction(new TransactionCallback<Object>() {
            @Override
            public Object call(Session session) throws Exception {
                new ServiceImporter().doExport();
                return null;
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

        OptionGroup optionGroup = new OptionGroup();
        optionGroup.addOption(
            new Option("import", "import services conf from 5.0 to SDSB"));
        optionGroup.addOption(
            new Option("export", "export services conf from SDSB to 5.0"));
        optionGroup.addOption(
            new Option("checksdsb", "check if SDSB can be activated"));

        options.addOptionGroup(optionGroup);

        return optionsParser.parse(options, args);
    }
}
