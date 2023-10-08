/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.monitor.JmxStringifiedData;

import java.io.IOException;

/**
 * Created by sjk on 11/12/15.
 */
public class XroadProcessLister extends ProcessLister {

    protected static final String PS_FORMAT = "--format user,pcpu,start_time,pmem,pid,command";
    private static final String LIST_XROAD_PIDS_COMMAND = "pgrep -u xroad java";

    /**
     * Program entry point
     */
    public static void main(String[] args) throws IOException {
        JmxStringifiedData<ProcessInfo> p = new XroadProcessLister().list();
        System.out.println("raw: " + p.getJmxStringData());
        System.out.println("parsed: " + p.getDtoData());
    }

    @Override
    protected String getCommand() {
        StringBuilder command = new StringBuilder("ps ");
        command.append(PS_FORMAT);
        command.append(" --pid $(");
        command.append(LIST_XROAD_PIDS_COMMAND);
        command.append(") ");
        return command.toString();
    }

}
