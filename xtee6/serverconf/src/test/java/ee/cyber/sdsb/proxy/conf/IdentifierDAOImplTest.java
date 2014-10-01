package ee.cyber.sdsb.proxy.conf;

import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.cyber.sdsb.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.cyber.sdsb.common.identifier.*;

import static org.junit.Assert.assertEquals;

public class IdentifierDAOImplTest {

    private Session session;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TestUtil.prepareDB();
    }

    @Before
    public void beginTransaction() {
        session = ServerConfDatabaseCtx.get().beginTransaction();
    }

    @After
    public void commitTransaction() {
        ServerConfDatabaseCtx.get().commitTransaction();
    }

    @Test
    public void clientId() throws Exception {
        assertCreateRead(new IdentifierCallback<ClientId>() {
            @Override
            public ClientId create() {
                return ClientId.create("EE", "class", "code1");
            }
        });

        assertCreateRead(new IdentifierCallback<ClientId>() {
            @Override
            public ClientId create() {
                return ClientId.create("EE", "class", "code2");
            }
        });
    }

    @Test
    public void serviceId() throws Exception {
        assertCreateRead(new IdentifierCallback<ServiceId>() {
            @Override
            public ServiceId create() {
                return ServiceId.create("EE", "cls", "code", null, "service1");
            }
        });

        assertCreateRead(new IdentifierCallback<ServiceId>() {
            @Override
            public ServiceId create() {
                return ServiceId.create("EE", "cls", "code", null, "service2");
            }
        });
    }

    @Test
    public void centralServiceId() throws Exception {
        assertCreateRead(new IdentifierCallback<CentralServiceId>() {
            @Override
            public CentralServiceId create() {
                return CentralServiceId.create("EE", "central1");
            }
        });

        assertCreateRead(new IdentifierCallback<CentralServiceId>() {
            @Override
            public CentralServiceId create() {
                return CentralServiceId.create("EE", "central2");
            }
        });
    }

    @Test
    public void globalGroupId() throws Exception {
        assertCreateRead(new IdentifierCallback<GlobalGroupId>() {
            @Override
            public GlobalGroupId create() {
                return GlobalGroupId.create("XX", "globalGroup1");
            }
        });

        assertCreateRead(new IdentifierCallback<GlobalGroupId>() {
            @Override
            public GlobalGroupId create() {
                return GlobalGroupId.create("XX", "globalGroup2");
            }
        });
    }

    @Test
    public void localGroupId() throws Exception {
        assertCreateRead(new IdentifierCallback<LocalGroupId>() {
            @Override
            public LocalGroupId create() {
                return LocalGroupId.create("localGroup1");
            }
        });

        assertCreateRead(new IdentifierCallback<LocalGroupId>() {
            @Override
            public LocalGroupId create() {
                return LocalGroupId.create("localGroup2");
            }
        });
    }

    @Test
    public void securityCategoryId() throws Exception {
        assertCreateRead(new IdentifierCallback<SecurityCategoryId>() {
            @Override
            public SecurityCategoryId create() {
                return SecurityCategoryId.create("XX", "cat1");
            }
        });

        assertCreateRead(new IdentifierCallback<SecurityCategoryId>() {
            @Override
            public SecurityCategoryId create() {
                return SecurityCategoryId.create("XX", "cat2");
            }
        });
    }

    @Test
    public void securityServerId() throws Exception {
        assertCreateRead(new IdentifierCallback<SecurityServerId>() {
            @Override
            public SecurityServerId create() {
                return SecurityServerId.create("XX", "class", "code", "srv1");
            }
        });

        assertCreateRead(new IdentifierCallback<SecurityServerId>() {
            @Override
            public SecurityServerId create() {
                return SecurityServerId.create("XX", "class", "code", "srv2");
            }
        });
    }

    private <T extends SdsbId> T get(T example) throws Exception {
        return IdentifierDAOImpl.getIdentifier(example);
    }

    private <T> void assertCreateRead(
            IdentifierCallback<? extends SdsbId> callback) throws Exception {
        SdsbId in = callback.create();
        session.save(in);

        SdsbId out = get(callback.create());
        assertEquals(in, out);
    }

    private interface IdentifierCallback<T extends SdsbId> {
        T create();
    }
}
