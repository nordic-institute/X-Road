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
package org.niis.xroad.securityserver.restapi.scheduling;

import ee.ria.xroad.common.SystemProperties;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Component
public class FirewallUpdater {

    private final GlobalConfProvider globalConfProvider;
    private final Firewall firewall;

    private boolean initialized;
    private Set<String> securityServersAddresses = new HashSet<>();

    @PostConstruct
    public void afterPropertiesSet() throws Exception {
        if (!globalConfProvider.isValid() || firewall.getClass().isAssignableFrom(NoopFirewall.class)) {
            initialized = false;
            return;
        }
        securityServersAddresses = globalConfProvider.getKnownAddresses();
        log.info("initial addresses: {}", securityServersAddresses.toString());

        // Check all addresses have rules

        initialized = true;
    }

    public void updateSecurityServers() {
        if (!initialized) {
            return;
        }
        // add code to ignore own address
        // ServerConfType serverConf = serverConfRepository.getServerConf();
        // SecurityServerId.Conf securityServerId = SecurityServerId.Conf.create(serverConf.getOwner().getIdentifier(), serverConf
        // .getServerCode());
        // globalConfProvider.getSecurityServerAddress(securityServerId)
        Set<String> globalConfKnownAddresses = globalConfProvider.getKnownAddresses();
        log.info("current addresses: {}", securityServersAddresses.toString());
        log.info("global conf addresses: {}", globalConfKnownAddresses.toString());
        Set<String> addressesToRemove = new HashSet<>();
        String groupName = SystemProperties.getFirewallSecurityServerGroupName();
        for (String securityServerAddress : securityServersAddresses) {
            if (!globalConfKnownAddresses.contains(securityServerAddress)) {
                if (InternetDomainName.isValid(securityServerAddress)) {
                    for (InetAddress inetAddress : domainNameToIpAddresses(securityServerAddress)) {
                        firewall.removeAllowAddressRule(inetAddress.getHostAddress(), groupName);
                    }
                } else if (InetAddresses.isInetAddress(securityServerAddress)) {
                    firewall.removeAllowAddressRule(securityServerAddress, groupName);
                }
                addressesToRemove.add(securityServerAddress);
            }
        }
        securityServersAddresses.removeAll(addressesToRemove);
        for (String globalConfKnownAddress : globalConfKnownAddresses) {
            if (!securityServersAddresses.contains(globalConfKnownAddress)) {
                if (InternetDomainName.isValid(globalConfKnownAddress)) {
                    for (InetAddress inetAddress : domainNameToIpAddresses(globalConfKnownAddress)) {
                        firewall.addAllowAddressRule(inetAddress.getHostAddress(), groupName);
                    }
                } else if (InetAddresses.isInetAddress(globalConfKnownAddress)) {
                    firewall.addAllowAddressRule(globalConfKnownAddress, groupName);
                } else {
                    log.warn("Didn't add rule, invalid address: {}", globalConfKnownAddress);
                    continue;
                }
                firewall.addAllowAddressRule(globalConfKnownAddress, groupName);
                securityServersAddresses.add(globalConfKnownAddress);
            }
        }
    }

    private static InetAddress[] domainNameToIpAddresses(String domainName) {
        try {
            InetAddress[] inetAddresses = InetAddress.getAllByName(domainName);
            log.info("Domain name was resolved to ip address(es): {}", Arrays.toString(inetAddresses));
            return inetAddresses;
        } catch (UnknownHostException e) {
            log.warn("Unknown host: {}", domainName);
            return new InetAddress[0];
        }
    }

}
