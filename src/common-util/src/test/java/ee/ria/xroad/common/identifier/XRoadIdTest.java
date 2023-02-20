/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.identifier;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class XRoadIdTest {
    private static final String INSTANCE = "instance";
    private static final String MEMBER_CLASS = "memberclass";
    private static final String MEMBER_CODE = "membercode";
    private static final String SUBSYSTEM_CODE = "subsystemcode";

    @Test
    public void shouldReturnSubsystemEncodedId() {

        var subsystemId = ClientId.Conf.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE, SUBSYSTEM_CODE);

        var result = subsystemId.asEncodedId();
        var expectation = StringUtils.join(new String[]{INSTANCE, MEMBER_CLASS, MEMBER_CODE, SUBSYSTEM_CODE},
                XRoadId.ENCODED_ID_SEPARATOR);

        assertThat(result).isEqualTo(expectation);
    }

    @Test
    public void shouldReturnEncodedId() {

        var subsystemId = ClientId.Conf.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);

        var result = subsystemId.asEncodedId();

        var expectation = StringUtils.join(new String[]{INSTANCE, MEMBER_CLASS, MEMBER_CODE},
                XRoadId.ENCODED_ID_SEPARATOR);

        assertThat(result).isEqualTo(expectation);
    }

    @Test
    public void shouldReturnEncodedIdWithType() {

        var subsystemId = ClientId.Conf.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);

        var result = subsystemId.asEncodedId(true);

        var expectation = StringUtils.join(new String[]{XRoadObjectType.MEMBER.toString(), INSTANCE, MEMBER_CLASS, MEMBER_CODE},
                XRoadId.ENCODED_ID_SEPARATOR);

        assertThat(result).isEqualTo(expectation);
    }
}
