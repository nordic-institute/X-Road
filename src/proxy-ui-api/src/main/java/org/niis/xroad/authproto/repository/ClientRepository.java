/**
 * The MIT License
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
package org.niis.xroad.authproto.repository;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.conf.serverconf.model.WsdlType;
import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.authproto.DatabaseContextHelper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Not sure if we are going to have this kind of repositories...
 */
@Component
public class ClientRepository {

    public static final int MEMBER_ID_PARTS = 3;

////    @Autowired
//    private SessionFactory sessionFactory = null;

    public List<MemberInfo> getAllMembers() {
        return GlobalConf.getMembers();
    }

    /**
     * transactions
     * test rollback
     * - correct id encoding (see rest proxy)
     * @param id
     */
    public ClientType getClient(String id) {
        ClientDAOImpl clientDAO = new ClientDAOImpl();
        List<String> parts = Arrays.asList(id.split(":"));
        String instance = parts.get(0);
        String memberClass = parts.get(1);
        String memberCode = parts.get(2);
        String subsystemCode = null;
        if (parts.size() > MEMBER_ID_PARTS) {
            subsystemCode = parts.get(MEMBER_ID_PARTS);
        }
        ClientId clientId = ClientId.create(instance, memberClass, memberCode, subsystemCode);

        return DatabaseContextHelper.serverConfTransaction(
                session -> {
                    ClientType client = clientDAO.getClient(session, clientId);
                    ClientType clientDto = copy(client);
                    return clientDto;
                });
    }

    /**
     * There may be a universal configuration which
     * tells jackson not to serialize non-initialized items -
     * may need to research depending on what type of dto
     * handling we need:
     * https://stackoverflow.com/questions/21708339/
     * avoid-jackson-serialization-on-non-fetched-lazy-objects/21760361#21760361
     * @param client
     * @return
     */
    private ClientType copy(ClientType client) {
        ClientType copy = new ClientType();
        BeanUtils.copyProperties(client, copy, "conf");
        for (WsdlType w: client.getWsdl()) {
            WsdlType wc = new WsdlType();
            BeanUtils.copyProperties(w, wc, "client");
            for (ServiceType s: wc.getService()) {
                ServiceType sc = new ServiceType();
                BeanUtils.copyProperties(s, sc, "requiredSecurityCategory");
                wc.getService().add(sc);
            }
            copy.getWsdl().add(wc);
        }
        return copy;
    }
}

