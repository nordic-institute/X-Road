package ee.cyber.xroad.monitoragent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataSender {
    private static final Logger LOG = LoggerFactory.getLogger(DataSender.class);

    static void send(String socket, MessageType messageType,
            Pair<MessageParam, String>[] contents) {
        LOG.debug("send({}, {}, {})",
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
        LOG.debug("Exec: {}", cmd);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        try {
            Process p = pb.start();
            int ret = p.waitFor();
            if (ret != 0) {
                LOG.error("Exec returned status {}", ret);
            }
        } catch (InterruptedException | IOException ex) {
            LOG.error("Exec failed", ex);
        }
    }
}
