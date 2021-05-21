/**
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
package org.niis.xroad.securityserver.restapi.converter;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.converter.PublicApiKeyDataConverter;
import org.niis.xroad.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.PublicApiKeyData;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.dto.PlaintextApiKeyDto;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test PublicApiKeyDataConverter
 */
public class PublicApiKeyDataConverterTest {

    private static final Long ID = 0L;
    private static final String PLAIN = "plain";
    private static final String ENCODED = "encoded";
    private PublicApiKeyDataConverter publicApiKeyDataConverter;

    @Before
    public void setup() {
        publicApiKeyDataConverter = new PublicApiKeyDataConverter();
    }

    @Test
    public void convertNewKey() throws InvalidRoleNameException {
        Set<Role> roles = Role.getForNames(Arrays.asList("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER"));
        PlaintextApiKeyDto key = new PlaintextApiKeyDto(ID, PLAIN, ENCODED, roles);

        PublicApiKeyData publicApiKeyData = publicApiKeyDataConverter.convert(key);
        assertEquals(ID, publicApiKeyData.getId());
        assertEquals(PLAIN, publicApiKeyData.getKey());
        assertEquals(2, publicApiKeyData.getRoles().size());
        assertTrue(publicApiKeyData.getRoles().contains(Role.XROAD_REGISTRATION_OFFICER));
    }

    @Test
    public void convertUpdatedKey() throws InvalidRoleNameException {
        Set<Role> roles = Role.getForNames(Arrays.asList("XROAD_SECURITY_OFFICER"));
        PersistentApiKeyType key = new PersistentApiKeyType(ENCODED, roles);

        PublicApiKeyData publicApiKeyData = publicApiKeyDataConverter.convert(key);
        assertEquals(null, publicApiKeyData.getId());
        assertEquals(null, publicApiKeyData.getKey());
        assertEquals(1, publicApiKeyData.getRoles().size());
        assertTrue(publicApiKeyData.getRoles().contains(Role.XROAD_SECURITY_OFFICER));
    }

    @Test
    public void convertList() throws InvalidRoleNameException {
        Set<Role> roles1 = Role.getForNames(Arrays.asList("XROAD_SECURITY_OFFICER"));
        PersistentApiKeyType key1 = new PersistentApiKeyType(ENCODED, roles1);
        Set<Role> roles2 = Role.getForNames(Arrays.asList("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER"));
        PersistentApiKeyType key2 = new PersistentApiKeyType(ENCODED, roles2);

        List<PublicApiKeyData> list = publicApiKeyDataConverter.convert(Arrays.asList(key1, key2));
        assertEquals(2, list.size());
    }
}
