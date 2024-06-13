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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder(toBuilder = true)
public class SharedParameters {
    private final String instanceIdentifier;
    private final List<ConfigurationSource> sources;
    private final List<ApprovedCA> approvedCAs;
    private final List<ApprovedTSA> approvedTSAs;
    private final List<Member> members;
    private final List<SecurityServer> securityServers;
    private final List<GlobalGroup> globalGroups;
    private final GlobalSettings globalSettings;

    public SharedParameters(String instanceIdentifier, List<ConfigurationSource> sources, List<ApprovedCA> approvedCAs,
                            List<ApprovedTSA> approvedTSAs, List<Member> members, List<SecurityServer> securityServers,
                            List<GlobalGroup> globalGroups, GlobalSettings globalSettings) {
        this.instanceIdentifier = instanceIdentifier;
        this.sources = sources;
        this.approvedCAs = approvedCAs;
        this.approvedTSAs = approvedTSAs;
        this.members = members;
        this.securityServers = securityServers;
        this.globalGroups = globalGroups;
        this.globalSettings = globalSettings;
    }



    @Data
    public static class ConfigurationSource {
        private String address;
        private List<byte[]> internalVerificationCerts;
        private List<byte[]> externalVerificationCerts;
    }

    @Data
    public static class Member {
        private MemberClass memberClass;
        private String memberCode;
        private String name;
        private List<Subsystem> subsystems;
        private ClientId id;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemberClass {
        private String code;
        private String description;
    }

    @Data
    @AllArgsConstructor
    public static class Subsystem {
        private String subsystemCode;
        private ClientId id;
    }

    @Data
    public static class ApprovedCA {
        private String name;
        private Boolean authenticationOnly;
        private CaInfo topCA;
        private List<CaInfo> intermediateCas;
        private String certificateProfileInfo;
        private AcmeServer acmeServer;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CaInfo {
        private byte[] cert;
        private List<OcspInfo> ocsp;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AcmeServer {
        private String directoryURL;
        private String ipAddress;
        private String authenticationCertificateProfileId;
        private String signingCertificateProfileId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OcspInfo {
        private String url;
        private byte[] cert;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApprovedTSA {
        private String name;
        private String url;
        private byte[] cert;
    }

    @Data
    public static class SecurityServer {
        private ClientId owner;
        private String serverCode;
        private String address;
        private List<CertHash> authCertHashes;
        private List<ClientId> clients;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GlobalGroup {
        private String groupCode;
        private String description;
        private List<ClientId> groupMembers;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GlobalSettings {
        private List<MemberClass> memberClasses;
        private Integer ocspFreshnessSeconds;
    }
}
