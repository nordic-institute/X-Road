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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

/**
 * Created by janne on 5.11.2015.
 */
@Slf4j
public class OsInfoLister extends AbstractExecLister<String> {

    private static final String SHOW_OS_INFO_COMMAND = "cat /proc/version";
    private static final int NUMBER_OF_FIELDS = 1;

    /**
     * Program entry point
     */
    public static void main(String[] args) throws IOException {
        JmxStringifiedData<String> p = new OsInfoLister().list();
        System.out.println("raw: " + p.getJmxStringData());
        System.out.println("parsed: " + p.getDtoData());
    }

    @Override
    protected String getCommand() {
        return SHOW_OS_INFO_COMMAND;
    }

    @Override
    protected Splitter getParsedDataSplitter() {
        return Splitter.on(CharMatcher.none());
    }

    @Override
    protected int numberOfColumnsToParse() {
        return NUMBER_OF_FIELDS;
    }

    @Override
    protected String parse(List<String> columns) {
        return columns.get(0);
    }
}
