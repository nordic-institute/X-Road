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

package org.niis.xroad.ss.test.addons.glue;

import com.nortal.test.asserts.Assertion;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.container.service.TestDatabaseService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class MessagelogStepDefs extends BaseStepDefs {

    @Autowired
    private TestDatabaseService testDatabaseService;

    @Step("Messagelog contains {int} entries with queryId {string}")
    public void messageLogContainsNEntries(int expectedCount, String queryId) {
        String sql = "SELECT COUNT(id) FROM logrecord WHERE queryid = :queryId";

        final Integer recordsCount = testDatabaseService.getMessagelogTemplate()
                .queryForObject(sql, Map.of("queryId", queryId), Integer.class);

        validate(recordsCount)
                .assertion(new Assertion.Builder()
                        .message("Verify count")
                        .expression("=")
                        .actualValue(recordsCount)
                        .expectedValue(expectedCount)
                        .build())
                .execute();
    }

}
