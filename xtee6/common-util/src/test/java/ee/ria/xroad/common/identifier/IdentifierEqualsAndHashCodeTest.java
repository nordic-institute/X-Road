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
package ee.ria.xroad.common.identifier;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.*;

/**
 * Tests to verify XROAD identifier hashCode and equals methods behavior.
 */
@RunWith(value = Parameterized.class)
public class IdentifierEqualsAndHashCodeTest {

    private abstract static class DataProvider {
        abstract XroadId provideVariant1();
        abstract XroadId provideVariant2();
        abstract XroadId provideVariant3();
        abstract XroadId provideVariant4();
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
                    XroadId provideVariant1() {
                        return ClientId.create(
                                "EE", "BUSINESS", "member");
                    }
                    @Override
                    XroadId provideVariant2() {
                        return ClientId.create(
                                "EE", "BUSINESS", "foobar");
                    }
                    @Override
                    XroadId provideVariant3() {
                        return ClientId.create(
                                "EE", "BUSINESS", "member", "foo");
                    }
                    @Override
                    XroadId provideVariant4() {
                        return ClientId.create(
                                "EE", "COMPANY", "member");
                    }
                },
            },
            {// Set #2 -- ServiceId
                new DataProvider() {
                    @Override
                    XroadId provideVariant1() {
                        return ServiceId.create(
                                "EE", "BUSINESS", "member", null, "getState");
                    }
                    @Override
                    XroadId provideVariant2() {
                        return ServiceId.create(
                                "EE", "BUSINESS", "member", null, "putState");
                    }
                    @Override
                    XroadId provideVariant3() {
                        return ServiceId.create(
                                "EE", "BUSINESS", "member", "foo", "getState");
                    }
                    @Override
                    XroadId provideVariant4() {
                        return ServiceId.create(
                                "EE", "COMPANY", "member", null, "getState");
                    }
                },
            },
            {// Set #3 -- CentralServiceId
                new DataProvider() {
                    @Override
                    XroadId provideVariant1() {
                        return CentralServiceId.create("EE", "a");
                    }
                    @Override
                    XroadId provideVariant2() {
                        return CentralServiceId.create("EE", "lorem_ipsum");
                    }
                    @Override
                    XroadId provideVariant3() {
                        return CentralServiceId.create("UK", "blah");
                    }
                    @Override
                    XroadId provideVariant4() {
                        return CentralServiceId.create("EE", "foo");
                    }
                },
            },
            {// Set #4 -- SecurityCategoryId
                new DataProvider() {
                    @Override
                    XroadId provideVariant1() {
                        return SecurityCategoryId.create("EE", "K1");
                    }
                    @Override
                    XroadId provideVariant2() {
                        return SecurityCategoryId.create("EE", "K2");
                    }
                    @Override
                    XroadId provideVariant3() {
                        return SecurityCategoryId.create("UK", "K3");
                    }
                    @Override
                    XroadId provideVariant4() {
                        return SecurityCategoryId.create("EE", "K4");
                    }
                },
            },
            {// Set #5 -- SecurityServerId
                new DataProvider() {
                    @Override
                    XroadId provideVariant1() {
                        return SecurityServerId.create(
                                "EE", "COMPANY", "producer", "server1");
                    }
                    @Override
                    XroadId provideVariant2() {
                        return SecurityServerId.create(
                                "EE", "COMPANY", "consumer", "server1");
                    }
                    @Override
                    XroadId provideVariant3() {
                        return SecurityServerId.create(
                                "EE", "BUSINESS", "producer", "server1");
                    }
                    @Override
                    XroadId provideVariant4() {
                        return SecurityServerId.create(
                                "EE", "COMPANY", "producer", "server3");
                    }
                },
            },
            {// Set #6 -- GlobalGroupId
                new DataProvider() {
                    @Override
                    XroadId provideVariant1() {
                        return GlobalGroupId.create("EE", "G1");
                    }
                    @Override
                    XroadId provideVariant2() {
                        return GlobalGroupId.create("EE", "G2");
                    }
                    @Override
                    XroadId provideVariant3() {
                        return GlobalGroupId.create("UE", "G1");
                    }
                    @Override
                    XroadId provideVariant4() {
                        return GlobalGroupId.create("EE", "G5");
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
        XroadId first = provider.provideVariant1();
        XroadId second = provider.provideVariant1();
        assertTrue(first.equals(second));
    }

    /**
     * Test case with unequal identifiers.
     */
    @Test
    public void shouldNotBeEqual() {
        XroadId first = provider.provideVariant1();
        XroadId second = provider.provideVariant2();
        assertFalse(first.equals(second));
    }

    /**
     * Test case with unequal identifiers.
     */
    @Test
    public void shouldNotBeEqual2() {
        XroadId first = provider.provideVariant1();
        XroadId second = provider.provideVariant3();
        assertFalse(first.equals(second));
    }

    /**
     * Test case with matching hash codes.
     */
    @Test
    public void hashCodeShouldMatch() {
        XroadId first = provider.provideVariant1();
        XroadId second = provider.provideVariant1();
        assertEquals(first.hashCode(), second.hashCode());
    }

    /**
     * Test case with non-matching hash codes.
     */
    @Test
    public void hashCodeShouldNotMatch() {
        XroadId first = provider.provideVariant1();
        XroadId second = provider.provideVariant4();
        assertNotEquals(first.hashCode(), second.hashCode());
    }

    /**
     * Test case to ensure equality after serialization.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void shouldSerializeAndDeserialize() throws Exception {
        XroadId inputId = provider.provideVariant1();

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
