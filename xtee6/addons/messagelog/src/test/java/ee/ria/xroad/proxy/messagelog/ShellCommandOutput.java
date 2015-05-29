package ee.ria.xroad.proxy.messagelog;

import lombok.Value;

/**
 * Result of shell command: exit status, standard output and standard error.
 */
@Value
class ShellCommandOutput {
    private int exitCode;
    private String standardOutput;
    private String standardError;

    boolean isError() {
        return exitCode != 0;
    }
}
