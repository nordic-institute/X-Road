/**
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
package org.niis.xroad.centralserver.restapi.service.managementrequest;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.entity.AuthCert;
import org.niis.xroad.centralserver.restapi.entity.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerId;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.AuthCertRepository;
import org.niis.xroad.centralserver.restapi.repository.AuthenticationCertificateRegistrationRequestRepository;
import org.niis.xroad.centralserver.restapi.repository.IdentifierRepository;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerClientRepository;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.niis.xroad.centralserver.restapi.service.exception.DataIntegrityException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.SUBMITTED_FOR_APPROVAL;
import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.WAITING;

/**
 * Service for handling authentication certificate registration requests
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthenticationCertificateRegistrationRequestHandler implements
        RequestHandler<AuthenticationCertificateRegistrationRequest> {

    private final IdentifierRepository<SecurityServerId> identifiers;
    private final SecurityServerClientRepository<XRoadMember> members;
    private final AuthenticationCertificateRegistrationRequestRepository authCertReqRequests;
    private final AuthCertRepository authCerts;
    private final SecurityServerRepository servers;

    /**
     * Creates an authentication certificate registration request.
     * In case automatic approval is enabled and prerequisites for approval are met,
     * the request is also fulfilled.
     * @param request request to add
     * @return information about the added request
     * @throws ValidationFailureException if request is not acceptable
     * @throws DataIntegrityException if request violates data integrity rules
     */
    public AuthenticationCertificateRegistrationRequest add(AuthenticationCertificateRegistrationRequest request) {
        SecurityServerId serverId = request.getSecurityServerId();
        Origin origin = request.getOrigin();

        if (Origin.CENTER.equals(origin)) {
            members.findOneBy(serverId.getOwner())
                    .getOrElseThrow(() ->
                            new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_OWNER_NOT_FOUND));
        }

        final byte[] validatedCert;
        try {
            final X509Certificate authCert = CryptoUtils.readCertificate(request.getAuthCert());
            if (!CertUtils.isAuthCert(authCert)) {
                throw new ValidationFailureException(ErrorMessage.INVALID_AUTH_CERTIFICATE);
            }

            //todo: verify that certificate is issued by a known CA

            validatedCert = authCert.getEncoded();
        } catch (CertificateParsingException | CertificateEncodingException e) {
            throw new ValidationFailureException(ErrorMessage.INVALID_AUTH_CERTIFICATE, e);
        }

        if (authCerts.existsByCert(validatedCert)) {
            throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SECURITY_SERVER_EXISTS);
        }

        List<AuthenticationCertificateRegistrationRequest> pendingRequests =
                authCertReqRequests.findByAuthCertAndStatus(validatedCert, EnumSet.of(WAITING, SUBMITTED_FOR_APPROVAL));

        AuthenticationCertificateRegistrationRequest authCertRegRequest;
        switch (pendingRequests.size()) {
            case 0:
                authCertRegRequest = newRequest(request);
                break;
            case 1:
                AuthenticationCertificateRegistrationRequest existingRequest = pendingRequests.get(0);
                Predicate<Void> isDifferentOrigin = __ ->
                        !existingRequest.getOrigin().equals(origin);
                Predicate<Void> isSameSecurityServerId = __ ->
                        existingRequest.getSecurityServerId().equals(serverId);
                if (isDifferentOrigin.and(isSameSecurityServerId).test(null)) {
                    authCertRegRequest = new AuthenticationCertificateRegistrationRequest(origin, existingRequest);
                    authCertRegRequest.getRequestProcessing().setStatus(SUBMITTED_FOR_APPROVAL);
                    break;
                }
                throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_EXISTS,
                        String.valueOf(existingRequest.getId()));
            default:
                throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_EXISTS);
        }

        authCertRegRequest.setAuthCert(validatedCert);
        authCertRegRequest.setAddress(request.getAddress());
        return authCertReqRequests.save(authCertRegRequest);
    }

    public boolean canAutoApprove(AuthenticationCertificateRegistrationRequest request) {
        return (SystemProperties.getCenterAutoApproveAuthCertRegRequests()
                || request.getProcessingStatus().equals(SUBMITTED_FOR_APPROVAL))
                && request.getOrigin() == Origin.SECURITY_SERVER
                && members.count(members.clientIdSpec(request.getSecurityServerId().getOwner())) > 0;
    }

    /**
     * Approves an authentication certificate registration request.
     * <br/>
     * <li>The owner member must exist or the approval fails.</li>
     * <li>If the security server does not exist, creates a new one.</li>
     * <li>Adds the certificate as a authentication certificate for the server</li>
     * @param request request to approve
     * @throws DataIntegrityException if request violates data integrity
     * @throws ValidationFailureException if request can not be approved
     */
    @Override
    public AuthenticationCertificateRegistrationRequest approve(AuthenticationCertificateRegistrationRequest request) {

        //check state
        if (!EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING).contains(request.getRequestProcessing().getStatus())) {
            throw new ValidationFailureException(ErrorMessage.MANAGEMENT_REQUEST_INVALID_STATE_FOR_APPROVAL,
                    String.valueOf(request.getId()));
        }

        SecurityServerId serverId = request.getSecurityServerId();

        //check prerequisites (member exists)
        XRoadMember owner = members
                .findOneBy(serverId.getOwner())
                .getOrElseThrow(() ->
                        new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_OWNER_NOT_FOUND));

        //create new security server if necessary
        final String serverCode = serverId.getServerCode();
        SecurityServer server = servers.findByOwnerAndServerCode(owner, serverCode)
                .getOrElse(() -> new SecurityServer(owner, serverCode));

        //register certificate
        server.getAuthCerts().add(new AuthCert(server, request.getAuthCert()));
        server.setAddress(request.getAddress());

        servers.save(server);
        request.setProcessingStatus(ManagementRequestStatus.APPROVED);

        //todo: handle global group registration

        return request;
    }

    @Override
    public Class<AuthenticationCertificateRegistrationRequest> requestType() {
        return AuthenticationCertificateRegistrationRequest.class;
    }

    private AuthenticationCertificateRegistrationRequest newRequest(
            AuthenticationCertificateRegistrationRequest request) {

        SecurityServerId serverId = identifiers.findOrCreate(request.getSecurityServerId());
        return new AuthenticationCertificateRegistrationRequest(request.getOrigin(), serverId);
    }

}
