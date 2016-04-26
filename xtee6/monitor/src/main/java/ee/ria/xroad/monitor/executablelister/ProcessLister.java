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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

/**
 * Created by janne.mattila on 28.10.2015.
 */
@Slf4j
public class ProcessLister extends AbstractExecLister<ProcessInfo> {

    /**
     * user        USER      effective user ID (alias uid).
     * <p>
     * pid         PID       a number representing the process ID (alias tgid).
     * <p>
     * ppid        PPID      parent process ID.
     * <p>
     * %cpu        %CPU      cpu utilization of the process in "##.#" format.  Currently, it is the CPU time
     * used divided by the time the process has been running (cputime/realtime ratio),
     * expressed as a percentage.  It will not add up to 100% unless you are lucky.
     * (alias pcpu).
     * <p>
     * start_time  START     starting time or date of the process.  Only the year will be displayed if the
     * process was not started the same year ps was invoked, or "MmmDD" if it was not
     * started the same day, or "HH:MM" otherwise.  See also bsdstart, start, lstart,
     * and stime.
     * <p>
     * %mem        %MEM      ratio of the process's resident set size  to the physical memory on the machine,
     * expressed as a percentage.  (alias pmem).
     * <p>
     * comm        COMMAND   command name (only the executable name).  Modifications to the command name will not
     * be shown.  A process marked <defunct> is partly dead, waiting to be fully destroyed by its parent.  The output
     * in this column may contain spaces.  (alias ucmd, ucomm).  See also the args format keyword, the -f option, and
     * the c option. When specified last, this colutilumn will extend to the edge of the display.  If ps can not
     * determine display width, as when output is redirected (piped) into a file or another command, the output width
     * is undefined (it may be 80, unlimited, determined by the TERM variable, and so on). The COLUMNS environment
     * variable or --cols option may be used to exactly determine the width in this case.  The w or -w option may be
     * also be used to adjust width.
     */

    protected static final String PS_FORMAT = "--format user,pcpu,start_time,pmem,pid,comm";
    protected static final String LIST_PROCESSES_COMMAND = "ps -aew " + PS_FORMAT;
    private static final int NUMBER_OF_FIELDS = 6;

    /**
     * Program entry point
     */
    public static void main(String[] args) throws IOException {
        ListedData<ProcessInfo> p = new ProcessLister().list();
        System.out.println("raw: " + p.getJmxData());
        System.out.println("parsed: " + p.getParsedData());
    }

    @Override
    protected String getCommand() {
        return LIST_PROCESSES_COMMAND;
    }

    @Override
    protected Splitter getParsedDataSplitter() {
        return Splitter.on(CharMatcher.WHITESPACE)
                .trimResults()
                .omitEmptyStrings()
                .limit(NUMBER_OF_FIELDS);
    }

    @Override
    protected int numberOfColumnsToParse() {
        return NUMBER_OF_FIELDS;
    }

    @Override
    protected ProcessInfo parse(List<String> columns) {
        ProcessInfo info = new ProcessInfo();
        int columnIndex = 0;
        info.setUserId(columns.get(columnIndex++));
        info.setCpuLoad(columns.get(columnIndex++));
        info.setStartTime(columns.get(columnIndex++));
        info.setMemUsed(columns.get(columnIndex++));
        info.setProcessId(columns.get(columnIndex++));
        // command should always be the last one (since it contains whitespace)
        info.setCommand(columns.get(columnIndex++));
        return info;
    }

    @Override
    boolean discardFirstDataLineFromParsed() {
        return true;
    }
}
