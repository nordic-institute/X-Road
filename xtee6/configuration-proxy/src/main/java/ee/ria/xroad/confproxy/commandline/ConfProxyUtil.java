package ee.ria.xroad.confproxy.commandline;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Paths;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.confproxy.ConfProxyProperties;

/**
 * Base for all the configuration proxy utility tools.
 */
@Getter
@RequiredArgsConstructor
public abstract class ConfProxyUtil {

    private static final int HELP_LINE_WIDTH = 80;
    private static final int HELP_LINE_PADDING = 3;
    private static final int HELP_LINE_DESCRIPTION_PADDING = 5;

    private final String name;

    private Options options = new Options();

    protected static final Option PROXY_INSTANCE =
            new Option("p", "proxy-instance", true,
                    "configuration-client proxy instance code");

    /**
     * Executes the utility program.
     * @param commandLine holds arguments for the utility program
     * @throws Exception in case of any errors
     */
    abstract void execute(CommandLine commandLine)
            throws Exception;

    /**
     * Prints the available arguments for the utility program.
     */
    protected final void printHelp() {
        final PrintWriter writer = new PrintWriter(System.out);
        final HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp(writer, HELP_LINE_WIDTH, name, "", options,
                HELP_LINE_PADDING, HELP_LINE_DESCRIPTION_PADDING, "", true);
        writer.close();
     }

    /**
     * Loads configuration proxy properties based on the instance provided
     * through the commandline.
     * @param commandLine holds arguments for the utility program
     * @return configuration proxy properties instance
     */
    protected final ConfProxyProperties loadConf(
            final CommandLine commandLine) {
        if (commandLine.hasOption(PROXY_INSTANCE.getLongOpt())) {
            String instance = commandLine
                    .getOptionValue(PROXY_INSTANCE.getOpt());
            try {
                return new ConfProxyProperties(instance);
            } catch (Exception e) {
                fail("Could not load configuration for '" + instance + "': "
                        + e.getMessage());
            }
        } else {
            printHelp();
            System.exit(0);
        }
        return null;
    }

    /**
     * Makes sure the configuration proxy instance that is requested from the
     * commandline exists.
     * @param commandLine holds arguments for the utility program
     */
    protected final void ensureProxyExists(final CommandLine commandLine) {
        if (commandLine.hasOption(PROXY_INSTANCE.getLongOpt())) {
            String instance = commandLine
                    .getOptionValue(PROXY_INSTANCE.getOpt());
            String confDir = SystemProperties.getConfigurationProxyConfPath();
            File instanceDir = Paths.get(confDir, instance).toFile();
            if (!instanceDir.exists()) {
                fail("Configuration for proxy instance '" + instance
                        + "' does not exist.");
            }
        }
    }

    /**
     * Abort the configuration proxy utility program with the provided message.
     * @param msg the error message to display
     */
    protected final void fail(final String msg) {
        System.err.println(msg);
        System.exit(1);
    }
}
