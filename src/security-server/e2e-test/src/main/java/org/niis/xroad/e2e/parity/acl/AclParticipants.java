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

package org.niis.xroad.e2e.parity.acl;

import org.niis.xroad.e2e.parity.acl.AclScenario.GlobalGroupSpec;

/**
 * The seeded identities and shared values the parity scenarios are written with.
 */
public final class AclParticipants {

    public static final String PROVIDER = "DEV:COM:1234:TestService";
    public static final String CONSUMER = "DEV:COM:4321:TestClient";
    public static final String OTHER_CONSUMER = "DEV:COM:1234:test-consumer";

    /** The member of the consumer subsystem, calling without a subsystem code. */
    public static final String MEMBER_OF_CONSUMER = "DEV:COM:4321";

    /** A Security Server code that no member of the instance registers. */
    public static final String UNREGISTERED_SECURITY_SERVER = "DEV:COM:1234:acl-parity-unregistered";

    /** The text the rows that disable a service with a notice expect back. */
    public static final String DISABLED_NOTICE = "Scheduled maintenance, please retry later";

    /** The member that owns the provider Security Server, named at member level by the monitoring rows. */
    public static final String OWNER_MEMBER = "DEV:COM:1234";

    /** The provider Security Server, named in the header of the monitoring rows. */
    public static final String OWNER_SECURITY_SERVER = "DEV:COM:1234:SS0";

    /** The client of the management Security Server that owns the management services. */
    public static final String MANAGEMENT = "DEV:COM:1234:MANAGEMENT";

    /** The management service that receives client registrations. */
    public static final String CLIENT_REG = "clientReg";

    /** The global group the Central Server fills with the owners of the registered Security Servers. */
    public static final String OWNERS_GROUP = "DEV:" + GlobalGroupSpec.SECURITY_SERVER_OWNERS;

    /** The code of the provider local group the group rows grant. */
    public static final String GROUP = "partners";

    /** The code of the global group the global group rows grant. */
    public static final String GLOBAL_GROUP = "everyone";

    private AclParticipants() {
    }
}
