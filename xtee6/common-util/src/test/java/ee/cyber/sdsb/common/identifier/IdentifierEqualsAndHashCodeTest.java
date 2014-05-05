package ee.cyber.sdsb.common.identifier;

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

@RunWith(value = Parameterized.class)
public class IdentifierEqualsAndHashCodeTest {

    private abstract static class DataProvider {
        abstract SdsbId provideVariant1();
        abstract SdsbId provideVariant2();
        abstract SdsbId provideVariant3();
        abstract SdsbId provideVariant4();
    }

    private final DataProvider provider;

    public IdentifierEqualsAndHashCodeTest(DataProvider provider) {
        this.provider = provider;
    }

    @Parameters
    public static Collection<DataProvider[]> data() {
        DataProvider[][] data = new DataProvider[][] {
            {   // Set #1 -- ClientId
                new DataProvider() {
                    @Override
                    SdsbId provideVariant1() {
                        return ClientId.create(
                                "EE", "BUSINESS", "member");
                    }
                    @Override
                    SdsbId provideVariant2() {
                        return ClientId.create(
                                "EE", "BUSINESS", "foobar");
                    }
                    @Override
                    SdsbId provideVariant3() {
                        return ClientId.create(
                                "EE", "BUSINESS", "member", "foo");
                    }
                    @Override
                    SdsbId provideVariant4() {
                        return ClientId.create(
                                "EE", "COMPANY", "member");
                    }
                },
            },
            {   // Set #2 -- ServiceId
                new DataProvider() {
                    @Override
                    SdsbId provideVariant1() {
                        return ServiceId.create(
                                "EE", "BUSINESS", "member", null, "getState");
                    }
                    @Override
                    SdsbId provideVariant2() {
                        return ServiceId.create(
                                "EE", "BUSINESS", "member", null, "putState");
                    }
                    @Override
                    SdsbId provideVariant3() {
                        return ServiceId.create(
                                "EE", "BUSINESS", "member", "foo", "getState");
                    }
                    @Override
                    SdsbId provideVariant4() {
                        return ServiceId.create(
                                "EE", "COMPANY", "member", null, "getState");
                    }
                },
            },
            {   // Set #3 -- CentralServiceId
                new DataProvider() {
                    @Override
                    SdsbId provideVariant1() {
                        return CentralServiceId.create("EE", "a");
                    }
                    @Override
                    SdsbId provideVariant2() {
                        return CentralServiceId.create("EE", "lorem_ipsum");
                    }
                    @Override
                    SdsbId provideVariant3() {
                        return CentralServiceId.create("UK", "blah");
                    }
                    @Override
                    SdsbId provideVariant4() {
                        return CentralServiceId.create("EE", "foo");
                    }
                },
            },
            {   // Set #4 -- SecurityCategoryId
                new DataProvider() {
                    @Override
                    SdsbId provideVariant1() {
                        return SecurityCategoryId.create("EE", "K1");
                    }
                    @Override
                    SdsbId provideVariant2() {
                        return SecurityCategoryId.create("EE", "K2");
                    }
                    @Override
                    SdsbId provideVariant3() {
                        return SecurityCategoryId.create("UK", "K3");
                    }
                    @Override
                    SdsbId provideVariant4() {
                        return SecurityCategoryId.create("EE", "K4");
                    }
                },
            },
            {   // Set #5 -- SecurityServerId
                new DataProvider() {
                    @Override
                    SdsbId provideVariant1() {
                        return SecurityServerId.create(
                                "EE", "COMPANY", "producer", "server1");
                    }
                    @Override
                    SdsbId provideVariant2() {
                        return SecurityServerId.create(
                                "EE", "COMPANY", "consumer", "server1");
                    }
                    @Override
                    SdsbId provideVariant3() {
                        return SecurityServerId.create(
                                "EE", "BUSINESS", "producer", "server1");
                    }
                    @Override
                    SdsbId provideVariant4() {
                        return SecurityServerId.create(
                                "EE", "COMPANY", "producer", "server3");
                    }
                },
            },
            {   // Set #6 -- GlobalGroupId
                new DataProvider() {
                    @Override
                    SdsbId provideVariant1() {
                        return GlobalGroupId.create("EE", "G1");
                    }
                    @Override
                    SdsbId provideVariant2() {
                        return GlobalGroupId.create("EE", "G2");
                    }
                    @Override
                    SdsbId provideVariant3() {
                        return GlobalGroupId.create("UE", "G1");
                    }
                    @Override
                    SdsbId provideVariant4() {
                        return GlobalGroupId.create("EE", "G5");
                    }
                },
            }
        };
        return Arrays.asList(data);
    }

    @Test
    public void shouldBeEqual() {
        SdsbId first = provider.provideVariant1();
        SdsbId second = provider.provideVariant1();
        assertTrue(first.equals(second));
    }

    @Test
    public void shouldNotBeEqual() {
        SdsbId first = provider.provideVariant1();
        SdsbId second = provider.provideVariant2();
        assertFalse(first.equals(second));
    }

    @Test
    public void shouldNotBeEqual2() {
        SdsbId first = provider.provideVariant1();
        SdsbId second = provider.provideVariant3();
        assertFalse(first.equals(second));
    }

    @Test
    public void hashCodeShouldMatch() {
        SdsbId first = provider.provideVariant1();
        SdsbId second = provider.provideVariant1();
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void hashCodeShouldNotMatch() {
        SdsbId first = provider.provideVariant1();
        SdsbId second = provider.provideVariant4();
        assertNotEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void shouldSerializeAndDeserialize() throws Exception {
        SdsbId inputId = provider.provideVariant1();

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
