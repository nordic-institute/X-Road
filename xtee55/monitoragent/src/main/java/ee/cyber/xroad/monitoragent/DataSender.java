package ee.cyber.xroad.monitoragent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
final class DataSender {

    private DataSender() {
    }

    static void send(String socket, MessageType messageType,
            Pair<MessageParam, String>[] contents) {
        log.debug("send({}, {}, {})",
                new Object[] {socket, messageType, Arrays.asList(contents)});

        List<String> cmdLine = makeCommandLine(socket, messageType, contents);
        exec(cmdLine);
    }

    private static List<String> makeCommandLine(String socket,
            MessageType messageType,
            Pair<MessageParam, String>[] contents) {
        ArrayList<String> ret = new ArrayList<>();

        ret.add(programName());
        ret.add(socket);
        ret.add(String.valueOf(messageType.code));

        for (Pair<MessageParam, String> field: contents) {
            ret.add(String.valueOf(field.getLeft().code));
            ret.add("S"); // All the fields are strings.
            ret.add(field.getRight());
        }

        return ret;
    }

    private static String programName() {
        return System.getProperty("ee.cyber.xroad.monitoragent.runProgram",
                "/usr/xtee/bin/unixsend");
    }

    private static void exec(List<String> cmd) {
        log.debug("Exec: {}", cmd);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        try {
            Process p = pb.start();
            int ret = p.waitFor();
            if (ret != 0) {
                log.error("Exec returned status {}", ret);
            }
        } catch (InterruptedException | IOException ex) {
            log.error("Exec failed", ex);
        }
    }
}
