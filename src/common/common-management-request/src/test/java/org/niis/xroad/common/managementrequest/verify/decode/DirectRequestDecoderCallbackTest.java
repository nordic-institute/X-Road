/*
 * The MIT License
 *
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
package org.niis.xroad.common.managementrequest.verify.decode;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.EjbcaSignCertificateProfileInfo;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.request.AuthCertRegRequestType;
import ee.ria.xroad.common.request.MemberDidRegEntryType;
import ee.ria.xroad.common.request.MemberDidRegRequestType;
import ee.ria.xroad.common.request.SystemDidRegRequestType;
import ee.ria.xroad.common.util.MimeTypes;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managemenetrequest.test.TestAuthRegTypeRequest;
import org.niis.xroad.common.managemenetrequest.test.TestBaseManagementRequest;
import org.niis.xroad.common.managemenetrequest.test.TestGenericClientRequest;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestBuilder;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestPayload;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.common.managementrequest.model.MultiPartOutputStream;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestVerifier;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.test.globalconf.EmptyGlobalConf;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_SIG_ALGO_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class DirectRequestDecoderCallbackTest {
    private static final String SYSTEM_DID = "did:web:ss1.example.org:v1:system";
    private static final String DSP_BASE_URL = "https://ss1.example.org:8443/dsp";
    private static final String ADDRESS = "ss1.example.org";

    private static KeyPair authKeyPair;
    private static KeyPair ownerKeyPair;

    private final TestGlobalConf globalConf = new TestGlobalConf();
    private final ManagementRequestVerifier verifier = new ManagementRequestVerifier(globalConf, new OcspVerifierFactory());
    private final SecurityServerId.Conf serverId = SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");

    @BeforeAll
    static void generateKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        authKeyPair = generator.generateKeyPair();
        ownerKeyPair = generator.generateKeyPair();
    }

    @Test
    void validSignedSystemDidRegPassesVerifierWithTypedFieldsIntact() throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var request = builder().buildSystemDidRegRequest(serverId, SYSTEM_DID, DSP_BASE_URL);

        var result = verify(authCert, request);

        assertThat(result.requestType()).isEqualTo(ManagementRequestType.SYSTEM_DID_REGISTRATION_REQUEST);
        assertThat(result.callback().attachedAuthCert()).contains(authCert);
        var typed = result.getRequest(SystemDidRegRequestType.class).orElseThrow();
        assertThat(typed.getServer()).isEqualTo(serverId);
        assertThat(typed.getSystemDid()).isEqualTo(SYSTEM_DID);
        assertThat(typed.getDspBaseUrl()).isEqualTo(DSP_BASE_URL);
    }

    @Test
    void ownerSignedMemberDidRegWithOwnerAsSenderReachesTypedCallback() throws Exception {
        var memberDids = memberDids();
        var request = builder().buildMemberDidRegRequest(serverId, memberDids);

        var payload = ownerSignedPayload(request);

        var result = verifier.readRequest(payload.getContentType(), payload.getPayloadAsStream());

        assertThat(result.requestType()).isEqualTo(ManagementRequestType.MEMBER_DID_REGISTRATION_REQUEST);
        var typed = result.getRequest(MemberDidRegRequestType.class).orElseThrow();
        assertThat(typed.getServer()).isEqualTo(serverId);
        assertThat(typed.getEntry()).extracting(MemberDidRegEntryType::getMember)
                .containsExactlyElementsOf(memberDids.keySet());
        assertThat(typed.getEntry()).extracting(MemberDidRegEntryType::getDid)
                .containsExactlyElementsOf(memberDids.values());
    }

    @Test
    void memberDidRegWithSenderOtherThanOwnerIsRejectedAsInvalidRequest() throws Exception {
        var otherSender = ClientId.Conf.create("DEV", "COM", "333");
        var request = new TestManagementRequestBuilder(otherSender, globalConf.getManagementRequestService())
                .buildMemberDidRegRequest(serverId, memberDids());
        var payload = ownerSignedPayload(request);

        var contentType = payload.getContentType();
        var content = payload.getPayloadAsStream();

        assertThatExceptionOfType(XrdRuntimeException.class)
                .isThrownBy(() -> verifier.readRequest(contentType, content))
                .satisfies(e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST.code());
                    assertThat(e.getDetails()).isEqualTo("Sender does not match server owner.");
                });
    }

    @Test
    void memberDidRegSignedByACertificateOfAnotherMemberIsRejectedAsInvalidRequest() throws Exception {
        var request = builder().buildMemberDidRegRequest(serverId, memberDids());
        var otherMemberCert = TestCertUtil.generateSignCert(ownerKeyPair.getPublic(),
                ClientId.Conf.create("DEV", "COM", "333"));
        var payload = new TestGenericClientRequest(otherMemberCert.getEncoded(), ownerOcspBytes(), ownerKeyPair.getPrivate(),
                request).createPayload();

        var contentType = payload.getContentType();
        var content = payload.getPayloadAsStream();

        assertThatExceptionOfType(XrdRuntimeException.class)
                .isThrownBy(() -> verifier.readRequest(contentType, content))
                .satisfies(e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST.code());
                    assertThat(e.getDetails()).startsWith("Subject identifier (").contains("(MEMBER:DEV/COM/333)")
                            .endsWith("in request");
                });
    }

    @Test
    void memberDidRegWithoutEntriesIsRejectedAsInvalidRequest() throws Exception {
        var request = builder().buildMemberDidRegRequest(serverId, Map.of());
        var payload = ownerSignedPayload(request);

        var contentType = payload.getContentType();
        var content = payload.getPayloadAsStream();

        assertThatExceptionOfType(XrdRuntimeException.class)
                .isThrownBy(() -> verifier.readRequest(contentType, content))
                .satisfies(e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST.code());
                    assertThat(e.getDetails()).isEqualTo("Request contains no entries");
                });
    }

    @Test
    void validSignedAuthCertRegPassesVerifierWithTypedFieldsIntact() throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var request = builder().buildAuthCertRegRequest(serverId, ADDRESS, authCert);

        var result = verify(authCert, request);

        assertThat(result.requestType()).isEqualTo(ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST);
        var typed = result.getRequest(AuthCertRegRequestType.class).orElseThrow();
        assertThat(typed.getServer()).isEqualTo(serverId);
        assertThat(typed.getAddress()).isEqualTo(ADDRESS);
        assertThat(typed.getAuthCert()).isEqualTo(authCert);
    }

    @ParameterizedTest
    @MethodSource("directRequestTypes")
    void missingAttachmentIsRejectedAsInvalidRequest(RequestFactory requestFactory) throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var envelope = partsEnvelope(requestFactory, authCert, authCert, EnumSet.of(Attachment.OWNER_OCSP), false);

        assertRejectedWith(envelope, ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @MethodSource("directRequestTypes")
    void missingOwnerSignatureIsRejectedAsInvalidRequest(RequestFactory requestFactory) throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var envelope = partsEnvelope(requestFactory, authCert, authCert, EnumSet.of(Attachment.OWNER_SIGNATURE), false);

        assertRejectedWith(envelope, ErrorCode.INVALID_REQUEST, "Owner signature algorithm id is missing");
    }

    @ParameterizedTest
    @MethodSource("directRequestTypesAndSignatureParts")
    void signatureWithoutAlgorithmIdIsRejectedAsInvalidRequest(RequestFactory requestFactory, Attachment signature,
                                                               String expectedDetails) throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var envelope = partsEnvelope(requestFactory, authCert, authCert, EnumSet.noneOf(Attachment.class), false)
                .withoutAlgorithmHeader(signature);

        assertRejectedWith(envelope, ErrorCode.INVALID_REQUEST, expectedDetails);
    }

    @ParameterizedTest
    @MethodSource("directRequestTypesAndUnknownAlgorithmIds")
    void signatureWithUnknownAlgorithmIdIsRejectedAsInvalidRequest(RequestFactory requestFactory,
                                                                   Attachment signature, String algorithmId,
                                                                   String expectedDetails) throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var envelope = partsEnvelope(requestFactory, authCert, authCert, EnumSet.noneOf(Attachment.class), false)
                .withAlgorithmHeaderValue(signature, algorithmId);

        assertRejectedWith(envelope, ErrorCode.INVALID_REQUEST, expectedDetails);
    }

    @ParameterizedTest
    @MethodSource("directRequestTypes")
    void extraAttachmentIsRejected(RequestFactory requestFactory) throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var envelope = partsEnvelope(requestFactory, authCert, authCert, EnumSet.noneOf(Attachment.class), true);

        assertRejectedWith(envelope, ErrorCode.INTERNAL_ERROR);
    }

    @ParameterizedTest
    @MethodSource("directRequestTypes")
    void authSignatureNotMadeByAttachedAuthCertIsRejected(RequestFactory requestFactory) throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var otherAuthCert = TestCertUtil.generateAuthCert(ownerKeyPair.getPublic());
        var envelope = partsEnvelope(requestFactory, authCert, otherAuthCert, EnumSet.noneOf(Attachment.class), false);

        assertRejectedWith(envelope, ErrorCode.INVALID_SIGNATURE_VALUE);
    }

    @ParameterizedTest
    @MethodSource("directRequestTypes")
    void ownerSignatureMadeByWrongKeyIsRejected(RequestFactory requestFactory) throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var request = requestFactory.build(builder(), serverId, authCert);
        var envelope = new PartsEnvelope(request, authCert, authKeyPair.getPrivate(), authKeyPair.getPrivate(),
                ownerCertBytes(), ownerOcspBytes(), EnumSet.noneOf(Attachment.class), false);

        assertRejectedWith(envelope, ErrorCode.INVALID_SIGNATURE_VALUE);
    }

    @Test
    void authCertRegWithAttachedCertDifferingFromRequestIsRejected() throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var otherAuthCert = TestCertUtil.generateAuthCert(ownerKeyPair.getPublic());
        var request = builder().buildAuthCertRegRequest(serverId, ADDRESS, otherAuthCert);
        var envelope = new PartsEnvelope(request, authCert, authKeyPair.getPrivate(), ownerKeyPair.getPrivate(),
                ownerCertBytes(), ownerOcspBytes(), EnumSet.noneOf(Attachment.class), false);

        assertRejectedWith(envelope, ErrorCode.CERT_VALIDATION);
    }

    private static Stream<Arguments> directRequestTypes() {
        RequestFactory authCertReg = (builder, server, authCert) -> builder.buildAuthCertRegRequest(server, ADDRESS, authCert);
        RequestFactory systemDidReg = (builder, server, authCert) ->
                builder.buildSystemDidRegRequest(server, SYSTEM_DID, DSP_BASE_URL);
        return Stream.of(
                Arguments.of(Named.of("authCertReg", authCertReg)),
                Arguments.of(Named.of("systemDidReg", systemDidReg)));
    }

    private static Stream<Arguments> directRequestTypesAndSignatureParts() {
        return directRequestTypes().flatMap(types -> Stream.of(
                Arguments.of(types.get()[0], Attachment.AUTH_SIGNATURE, "Auth signature algorithm id is missing"),
                Arguments.of(types.get()[0], Attachment.OWNER_SIGNATURE, "Owner signature algorithm id is missing")));
    }

    private static Stream<Arguments> directRequestTypesAndUnknownAlgorithmIds() {
        return directRequestTypes().flatMap(types -> Stream.of(
                Arguments.of(types.get()[0], Attachment.AUTH_SIGNATURE, "SHA512withFOO",
                        "Auth signature algorithm 'SHA512withFOO' is unknown"),
                Arguments.of(types.get()[0], Attachment.AUTH_SIGNATURE, "",
                        "Auth signature algorithm ' ' is unknown"),
                Arguments.of(types.get()[0], Attachment.OWNER_SIGNATURE, "SHA512withFOO",
                        "Owner signature algorithm 'SHA512withFOO' is unknown"),
                Arguments.of(types.get()[0], Attachment.OWNER_SIGNATURE, "",
                        "Owner signature algorithm ' ' is unknown")));
    }

    private PartsEnvelope partsEnvelope(RequestFactory requestFactory, byte[] signingCert, byte[] attachedAuthCert,
                                        Set<Attachment> omitted, boolean extraPart) throws Exception {
        var request = requestFactory.build(builder(), serverId, signingCert);
        return new PartsEnvelope(request, attachedAuthCert, authKeyPair.getPrivate(), ownerKeyPair.getPrivate(),
                ownerCertBytes(), ownerOcspBytes(), omitted, extraPart);
    }

    private void assertRejectedWith(TestBaseManagementRequest envelope, ErrorCode expected, String expectedDetails)
            throws Exception {
        var payload = envelope.createPayload();

        assertThatExceptionOfType(XrdRuntimeException.class)
                .isThrownBy(() -> verifier.readRequest(payload.getContentType(), payload.getPayloadAsStream()))
                .satisfies(e -> {
                    assertThat(e.getCode()).isEqualTo(expected.code());
                    assertThat(e.getDetails()).isEqualTo(expectedDetails);
                });
    }

    private void assertRejectedWith(TestBaseManagementRequest envelope, ErrorCode expected) throws Exception {
        var payload = envelope.createPayload();

        assertThatExceptionOfType(XrdRuntimeException.class)
                .isThrownBy(() -> verifier.readRequest(payload.getContentType(), payload.getPayloadAsStream()))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo(expected.code()));
    }

    private Map<ClientId.Conf, String> memberDids() {
        var memberDids = new LinkedHashMap<ClientId.Conf, String>();
        memberDids.put(serverId.getOwner(), "did:web:ss1.example.org:v1:member-a");
        memberDids.put(ClientId.Conf.create("DEV", "COM", "222", "SUB1"), "did:web:ss1.example.org:v1:member-b");
        memberDids.put(ClientId.Conf.create("DEV", "ORG", "444"), "did:web:ss1.example.org:v1:member-c");
        return memberDids;
    }

    private TestManagementRequestPayload ownerSignedPayload(SoapMessageImpl request) throws Exception {
        var ownerCert = TestCertUtil.generateSignCert(ownerKeyPair.getPublic(), serverId.getOwner());
        return new TestGenericClientRequest(ownerCert.getEncoded(), ownerOcspBytes(), ownerKeyPair.getPrivate(), request)
                .createPayload();
    }

    private byte[] ownerCertBytes() throws Exception {
        return TestCertUtil.generateSignCert(ownerKeyPair.getPublic(), serverId.getOwner()).getEncoded();
    }

    private byte[] ownerOcspBytes() throws Exception {
        var ownerCert = TestCertUtil.generateSignCert(ownerKeyPair.getPublic(), serverId.getOwner());
        return OcspTestUtils.createOCSPResponse(ownerCert,
                TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key,
                CertificateStatus.GOOD).getEncoded();
    }

    private interface RequestFactory {
        SoapMessageImpl build(TestManagementRequestBuilder builder, SecurityServerId.Conf server, byte[] authCert)
                throws Exception;
    }

    private enum Attachment {
        AUTH_SIGNATURE, OWNER_SIGNATURE, AUTH_CERT, OWNER_CERT, OWNER_OCSP
    }

    private static final class PartsEnvelope extends TestBaseManagementRequest {
        private final byte[] authCert;
        private final PrivateKey authKey;
        private final PrivateKey ownerKey;
        private final byte[] ownerCert;
        private final byte[] ownerOcsp;
        private final Set<Attachment> omitted;
        private final boolean extraPart;
        private final Set<Attachment> withoutAlgorithmHeader = EnumSet.noneOf(Attachment.class);
        private final Map<Attachment, String> algorithmHeaderValues = new EnumMap<>(Attachment.class);

        PartsEnvelope(SoapMessageImpl request, byte[] authCert, PrivateKey authKey, PrivateKey ownerKey,
                      byte[] ownerCert, byte[] ownerOcsp, Set<Attachment> omitted, boolean extraPart) {
            super(request.getBytes());
            this.authCert = authCert;
            this.authKey = authKey;
            this.ownerKey = ownerKey;
            this.ownerCert = ownerCert;
            this.ownerOcsp = ownerOcsp;
            this.omitted = omitted;
            this.extraPart = extraPart;
        }

        PartsEnvelope withoutAlgorithmHeader(Attachment signature) {
            withoutAlgorithmHeader.add(signature);
            return this;
        }

        PartsEnvelope withAlgorithmHeaderValue(Attachment signature, String value) {
            algorithmHeaderValues.put(signature, value);
            return this;
        }

        @Override
        protected void writeMultipart(MultiPartOutputStream multipart) throws Exception {
            var algorithm = SignAlgorithm.SHA512_WITH_RSA;
            writePart(multipart, Attachment.AUTH_SIGNATURE, createSignature(authKey, algorithm),
                    algorithmHeader(Attachment.AUTH_SIGNATURE, algorithm));
            writePart(multipart, Attachment.OWNER_SIGNATURE, createSignature(ownerKey, algorithm),
                    algorithmHeader(Attachment.OWNER_SIGNATURE, algorithm));
            writePart(multipart, Attachment.AUTH_CERT, authCert);
            writePart(multipart, Attachment.OWNER_CERT, ownerCert);
            writePart(multipart, Attachment.OWNER_OCSP, ownerOcsp);
            if (extraPart) {
                multipart.startPart(MimeTypes.BINARY);
                multipart.write(ownerOcsp);
            }
        }

        private String[] algorithmHeader(Attachment signature, SignAlgorithm algorithm) {
            return withoutAlgorithmHeader.contains(signature)
                    ? new String[0]
                    : new String[] {HEADER_SIG_ALGO_ID + ": " + algorithmHeaderValues.getOrDefault(signature, algorithm.name())};
        }

        private void writePart(MultiPartOutputStream multipart, Attachment attachment, byte[] content,
                               String... headers) throws Exception {
            if (!omitted.contains(attachment)) {
                multipart.startPart(MimeTypes.BINARY, headers);
                multipart.write(content);
            }
        }
    }

    private TestManagementRequestBuilder builder() {
        return new TestManagementRequestBuilder(serverId.getOwner(), globalConf.getManagementRequestService());
    }

    private ManagementRequestVerifier.Result verify(byte[] authCert, SoapMessageImpl request)
            throws Exception {
        var payload = envelope(authCert, request, authKeyPair.getPrivate()).createPayload();
        return verifier.readRequest(payload.getContentType(), payload.getPayloadAsStream());
    }

    private TestAuthRegTypeRequest envelope(byte[] authCert, SoapMessageImpl request,
                                            PrivateKey authSigningKey) throws Exception {
        var ownerCert = TestCertUtil.generateSignCert(ownerKeyPair.getPublic(), serverId.getOwner());
        var ownerOcsp = OcspTestUtils.createOCSPResponse(ownerCert,
                TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key,
                CertificateStatus.GOOD);
        return new TestAuthRegTypeRequest(authCert, ownerCert.getEncoded(), ownerOcsp.getEncoded(), request,
                authSigningKey, ownerKeyPair.getPrivate());
    }

    private static final class TestGlobalConf extends EmptyGlobalConf {
        private final ClientId.Conf managementService = ClientId.Conf.create("DEV", "COM", "MGMT", "MANAGEMENT");

        @Override
        public String getInstanceIdentifier() {
            return "DEV";
        }

        @Override
        public int getOcspFreshnessSeconds() {
            return Integer.MAX_VALUE / 2;
        }

        @Override
        public X509Certificate getCaCert(String instanceIdentifier, X509Certificate orgCert) {
            var ca = TestCertUtil.getCaCert();
            if (getInstanceIdentifier().equals(instanceIdentifier)
                    && ca.getSubjectX500Principal().equals(orgCert.getIssuerX500Principal())) {
                return ca;
            }
            throw XrdRuntimeException.systemInternalError("Certificate is not issued by approved certification service provider.");
        }

        @Override
        public SignCertificateProfileInfo getSignCertificateProfileInfo(
                SignCertificateProfileInfo.Parameters parameters, X509Certificate cert) {
            return new EjbcaSignCertificateProfileInfo(parameters);
        }

        @Override
        public ClientId.Conf getSubjectName(SignCertificateProfileInfo.Parameters parameters, X509Certificate cert) {
            return getSignCertificateProfileInfo(parameters, cert).getSubjectIdentifier(cert);
        }

        @Override
        public ClientId getManagementRequestService() {
            return managementService;
        }
    }
}
