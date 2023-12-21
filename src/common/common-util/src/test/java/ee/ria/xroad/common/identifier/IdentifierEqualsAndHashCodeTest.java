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
package ee.ria.xroad.common.identifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests to verify X-Road identifier hashCode and equals methods behavior.
 */
@RunWith(value = Parameterized.class)
public class IdentifierEqualsAndHashCodeTest {

    private abstract static class DataProvider {
        abstract XRoadId provideVariant1();
        abstract XRoadId provideVariant2();
        abstract XRoadId provideVariant3();
        abstract XRoadId provideVariant4();
    }

    private final DataProvider provider;

    /**
     * Constructs tests with given parameter data provider.
     * @param provider the data provider
     */
    public IdentifierEqualsAndHashCodeTest(DataProvider provider) {
        this.provider = provider;
    }

    /**
     * @return test parameters
     */
    @Parameters
    public static Collection<DataProvider[]> data() {
        DataProvider[][] data = new DataProvider[][] {
            {// Set #1 -- ClientId
                new DataProvider() {
                    @Override
                    XRoadId provideVariant1() {
                        return ClientId.Conf.create(
                                "EE", "BUSINESS", "member");
                    }
                    @Override
                    XRoadId provideVariant2() {
                        return ClientId.Conf.create(
                                "EE", "BUSINESS", "foobar");
                    }
                    @Override
                    XRoadId provideVariant3() {
                        return ClientId.Conf.create(
                                "EE", "BUSINESS", "member", "foo");
                    }
                    @Override
                    XRoadId provideVariant4() {
                        return ClientId.Conf.create(
                                "EE", "COMPANY", "member");
                    }
                },
            },
            {// Set #2 -- ServiceId
                new DataProvider() {
                    @Override
                    XRoadId provideVariant1() {
                        return ServiceId.Conf.create(
                                "EE", "BUSINESS", "member", null, "getState");
                    }
                    @Override
                    XRoadId provideVariant2() {
                        return ServiceId.Conf.create(
                                "EE", "BUSINESS", "member", null, "putState");
                    }
                    @Override
                    XRoadId provideVariant3() {
                        return ServiceId.Conf.create(
                                "EE", "BUSINESS", "member", "foo", "getState");
                    }
                    @Override
                    XRoadId provideVariant4() {
                        return ServiceId.Conf.create(
                                "EE", "COMPANY", "member", null, "getState");
                    }
                },
            },
            {// Set #5 -- SecurityServerId
                new DataProvider() {
                    @Override
                    XRoadId provideVariant1() {
                        return SecurityServerId.Conf.create(
                                "EE", "COMPANY", "producer", "server1");
                    }
                    @Override
                    XRoadId provideVariant2() {
                        return SecurityServerId.Conf.create(
                                "EE", "COMPANY", "consumer", "server1");
                    }
                    @Override
                    XRoadId provideVariant3() {
                        return SecurityServerId.Conf.create(
                                "EE", "BUSINESS", "producer", "server1");
                    }
                    @Override
                    XRoadId provideVariant4() {
                        return SecurityServerId.Conf.create(
                                "EE", "COMPANY", "producer", "server3");
                    }
                },
            },
            {// Set #6 -- GlobalGroupId
                new DataProvider() {
                    @Override
                    XRoadId provideVariant1() {
                        return GlobalGroupId.Conf.create("EE", "G1");
                    }
                    @Override
                    XRoadId provideVariant2() {
                        return GlobalGroupId.Conf.create("EE", "G2");
                    }
                    @Override
                    XRoadId provideVariant3() {
                        return GlobalGroupId.Conf.create("UE", "G1");
                    }
                    @Override
                    XRoadId provideVariant4() {
                        return GlobalGroupId.Conf.create("EE", "G5");
                    }
                },
            }
        };
        return Arrays.asList(data);
    }

    /**
     * Test case with equal identifiers.
     */
    @Test
    public void shouldBeEqual() {
        XRoadId first = provider.provideVariant1();
        XRoadId second = provider.provideVariant1();
        assertEquals(first, second);
    }

    /**
     * Test case with unequal identifiers.
     */
    @Test
    public void shouldNotBeEqual() {
        XRoadId first = provider.provideVariant1();
        XRoadId second = provider.provideVariant2();
        assertNotEquals(first, second);
    }

    /**
     * Test case with unequal identifiers.
     */
    @Test
    public void shouldNotBeEqual2() {
        XRoadId first = provider.provideVariant1();
        XRoadId second = provider.provideVariant3();
        assertNotEquals(first, second);
    }

    /**
     * Test case with matching hash codes.
     */
    @Test
    public void hashCodeShouldMatch() {
        XRoadId first = provider.provideVariant1();
        XRoadId second = provider.provideVariant1();
        assertEquals(first.hashCode(), second.hashCode());
    }

    /**
     * Test case with non-matching hash codes.
     */
    @Test
    public void hashCodeShouldNotMatch() {
        XRoadId first = provider.provideVariant1();
        XRoadId second = provider.provideVariant4();
        assertNotEquals(first.hashCode(), second.hashCode());
    }

    /**
     * Test case to ensure equality after serialization.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void shouldSerializeAndDeserialize() throws Exception {
        XRoadId inputId = provider.provideVariant1();

        PipedInputStream pin = new PipedInputStream();
        PipedOutputStream pos = new PipedOutputStream(pin);

        ObjectOutputStream oos = new ObjectOutputStream(pos);
        ObjectInputStream oin = new ObjectInputStream(pin);

        oos.writeObject(inputId);

        Object outputId = oin.readObject();
        assertNotNull(outputId);
        assertEquals(inputId, outputId);
    }
}
