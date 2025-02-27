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
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class NftablesFirewall implements Firewall {

    private static final String SECURITY_SERVER_MESSAGE_PORT = "5500";
    private static final String SECURITY_SERVER_OCSP_PORT = "5577";

    private final ExternalProcessRunner externalProcessRunner;

    public void init() {
        String setName = SystemProperties.getFirewallSecurityServerGroupName();
        addAllowSetRule(setName, SECURITY_SERVER_MESSAGE_PORT);
        addAllowSetRule(setName, SECURITY_SERVER_OCSP_PORT);
    }

    private void addAllowSetRule(String setName, String port) {
        try {
            // put into script, add sudoer rule for script and call script from here
            ExternalProcessRunner.ProcessResult addSetResult = externalProcessRunner.execute("sudo", "nft",
                    "add",
                    "set",
                    "inet",
                    "firewall",
                    setName,
                    "{",
                    "type",
                    "ipv4_addr;",
                    "}");
            log.info("nft add set result: {}", addSetResult.getProcessOutput());
            ExternalProcessRunner.ProcessResult addRuleResult = externalProcessRunner.execute("sudo", "nft",
                    "add",
                    "rule",
                    "inet",
                    "firewall",
                    "inbound",
                    "ip",
                    "saddr",
                    "@" + setName,
                    "tcp",
                    "dport",
                    port,
                    "accept");
            log.info("nft add rule result: {}", addRuleResult.getProcessOutput());
        } catch (ProcessNotExecutableException | ProcessFailedException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addAllowAddressRule(String ipAddress, String setName) {
        try {
            // resolve domain name and in case of multiple ip-addresses call for each
            externalProcessRunner.execute("sudo", "nft", "add", "element", "inet", "firewall", setName, "{", ipAddress, "}");
        } catch (ProcessNotExecutableException | ProcessFailedException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAllowAddressRule(String address, String groupName) {

    }

}
