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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.DnFieldValue;
import ee.ria.xroad.common.certificateprofile.impl.DnFieldDescriptionImpl;
import ee.ria.xroad.common.certificateprofile.impl.DnFieldValueImpl;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test for DnFieldHelper
 */
public class DnFieldHelperTest {

    public static final String FIELD_1 = "field1";
    public static final String FIELD_2 = "field2";
    public static final String FIELD_1_DEFAULT = "field1default";
    public static final String FIELD_2_DEFAULT = "field2default";
    private DnFieldHelper helper = new DnFieldHelper();

    @Test
    public void createSubjectName() {
        List<DnFieldValue> fieldValues = Arrays.asList(
                new DnFieldValueImpl("O", "foo"),
                new DnFieldValueImpl("CN", "bar"));
        assertEquals("O=foo, CN=bar", helper.createSubjectName(fieldValues));
    }


    @Test
    public void processDnParameters() throws Exception {
        DnFieldDescription field1ReadOnly = new DnFieldDescriptionImpl(FIELD_1, "x", FIELD_1_DEFAULT)
                .setReadOnly(true);
        DnFieldDescription field2Editable = new DnFieldDescriptionImpl(FIELD_2, "x", FIELD_2_DEFAULT)
                .setReadOnly(false);
        // read only
        // no param
        List<DnFieldValue> values = helper.processDnParameters(
                new DnFieldTestCertificateProfileInfo(field1ReadOnly, true),
                new HashMap<>());
        assertTrue(values.size() == 1);
        assertEquals(new DnFieldValueImpl(FIELD_1, FIELD_1_DEFAULT), values.iterator().next());

        // attempt to set param is ignored
        values = helper.processDnParameters(
                new DnFieldTestCertificateProfileInfo(field1ReadOnly, true),
                ImmutableMap.of(FIELD_1, "bar"));
        assertTrue(values.size() == 1);
        assertEquals(new DnFieldValueImpl(FIELD_1, FIELD_1_DEFAULT), values.iterator().next());

        // extra param
        try {
            helper.processDnParameters(new DnFieldTestCertificateProfileInfo(field1ReadOnly, true),
                    ImmutableMap.of("foo", "bar"));
            fail("should throw exception");
        } catch (DnFieldHelper.InvalidDnParameterException expected) {
        }

        // editable field
        // no param
        try {
            helper.processDnParameters(
                    new DnFieldTestCertificateProfileInfo(field2Editable, true),
                    new HashMap<>());
            fail("should throw exception");
        } catch (DnFieldHelper.InvalidDnParameterException expected) {
        }

        // set param
        values = helper.processDnParameters(
                new DnFieldTestCertificateProfileInfo(field2Editable, true),
                ImmutableMap.of(FIELD_2, "bar"));
        assertTrue(values.size() == 1);
        assertEquals(new DnFieldValueImpl(FIELD_2, "bar"), values.iterator().next());

        // extra param 1
        try {
            helper.processDnParameters(new DnFieldTestCertificateProfileInfo(field2Editable, true),
                    ImmutableMap.of("foo", "bar"));
            fail("should throw exception");
        } catch (DnFieldHelper.InvalidDnParameterException expected) {
        }

        // extra param 2
        try {
            helper.processDnParameters(new DnFieldTestCertificateProfileInfo(field2Editable, true),
                    ImmutableMap.of(FIELD_2, "bar", "foo", "bar2"));
            fail("should throw exception");
        } catch (DnFieldHelper.InvalidDnParameterException expected) {
        }

        // invalid param
        try {
            values = helper.processDnParameters(
                    new DnFieldTestCertificateProfileInfo(field2Editable, false),
                    ImmutableMap.of(FIELD_2, "bar"));
            fail("should throw exception");
        } catch (DnFieldHelper.InvalidDnParameterException expected) {
        }
    }
}
