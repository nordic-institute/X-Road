/**
 * The MIT License
 *
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
package org.niis.xroad.cs.admin.rest.api.converter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public abstract class AbstractDtoConverterTest {

    protected static final String INSTANCE_ID = "TEST";
    protected static final String MEMBER_CLASS_CODE = "MEMBER_CLASS";
    protected static final String MEMBER_CODE = "MEMBER_CODE";
    protected static final String MEMBER_NAME = "MEMBER_NAME";
    protected static final String SUBSYSTEM_CODE = "SUBSYSTEM";
    protected static final String MEMBER_ID_STRING = "MEMBER_ID_STRING";
    protected static final String SUBSYSTEM_ID_STRING = "SUBSYSTEM_ID_STRING";

    protected static final String SECURITY_SERVER_ID_STRING = "SECURITY_SERVER_ID_STRING";

    protected static final String SERVER_ADDRESS = "SERVER_ADDRESS";

    protected static final String SERVER_CODE = "SERVER_ADDRESS";

    protected final ZoneOffset dtoZoneOffset = ZoneOffset.UTC;
    protected final Instant createdAtInstance = Instant.parse("2022-06-15T20:00:00Z");
    protected final Instant updatedAtInstance = createdAtInstance.plus(1, ChronoUnit.HOURS);
    protected final OffsetDateTime createdAtOffsetDateTime = createdAtInstance.atOffset(dtoZoneOffset);
    protected final OffsetDateTime updatedAtOffsetDateTime = updatedAtInstance.atOffset(dtoZoneOffset);

}
