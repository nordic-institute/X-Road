/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.monitor.executablelister;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by sjk on 11/12/15.
 */
public class XroadProcessLister extends ProcessLister {

    protected static final String PS_FORMAT = "--format user,pcpu,start_time,pmem,pid,command";
    private static final String PIDS_TO_ONE_LINE_COMMAND = " | paste -sd ',' -";
    private static final String UBUNTU_LIST_XROAD_PIDS_COMMAND =
            "initctl list |grep xroad | grep running |grep -o '[0-9]*'";
    private static final String RH_LIST_XROAD_PIDS_COMMAND =
            "for i in `systemctl list-units --type service | grep xroad | grep running | cut --delimiter"
                + " ' ' --field 1`;do systemctl show $i --property=MainPID | cut --delimiter '=' --field 2; done";

    /**
     * Program entry point
     */
    public static void main(String[] args) throws IOException {
        ListedData<ProcessInfo> p = new XroadProcessLister().list();
        System.out.println("raw: " + p.getJmxData());
        System.out.println("parsed: " + p.getParsedData());
    }

    @Override
    protected String getCommand() {
        StringBuilder command = new StringBuilder("ps --pid ");
        command.append("$(");
        if (Files.exists(Paths.get("/etc/redhat-release"))) {
            command.append(RH_LIST_XROAD_PIDS_COMMAND);
        } else {
            command.append(UBUNTU_LIST_XROAD_PIDS_COMMAND);
        }
        command.append(PIDS_TO_ONE_LINE_COMMAND);
        command.append(") ");
        command.append(PS_FORMAT);

        return command.toString();
    }
}
