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

import com.google.common.base.Splitter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by janne on 4.11.2015.
 */
@Slf4j
public class PackageLister extends AbstractExecLister<PackageInfo> {

    private static final String UBUNTU_LIST_PACKAGES_COMMAND =
            "dpkg-query --show --showformat '${Package}/${Version}\n'";
    private static final String REDHAT_LIST_PACKAGES_COMMAND =
            "rpm -qa --queryformat '%{NAME}/%{VERSION}-%{RELEASE}\n'";
    private static final int NUMBER_OF_FIELDS = 2;

    /**
     * Program entry point
     */
    public static void main(String[] args) throws IOException {
        ListedData<PackageInfo> p = new PackageLister().list();
        System.out.println("raw: " + p.getJmxData());
        System.out.println("parsed: " + p.getParsedData());
    }

    @Override
    protected String getCommand() {
        if (Files.exists(Paths.get("/etc/redhat-release"))) {
            return REDHAT_LIST_PACKAGES_COMMAND;
        } else {
            return UBUNTU_LIST_PACKAGES_COMMAND;
        }
    }

    @Override
    protected Splitter getParsedDataSplitter() {
        return Splitter.on("/")
                .trimResults()
                .omitEmptyStrings()
                .limit(NUMBER_OF_FIELDS);
    }

    @Override
    protected int numberOfColumnsToParse() {
        return NUMBER_OF_FIELDS;
    }

    @Override
    protected PackageInfo parse(List<String> columns) {
        PackageInfo info = new PackageInfo();
        int columnIndex = 0;
        info.setName(columns.get(columnIndex++));
        info.setVersion(columns.get(columnIndex++));
        return info;
    }
}
