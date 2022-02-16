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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.dto.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.centralserver.restapi.entity.AuthCert;
import org.niis.xroad.centralserver.restapi.entity.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.centralserver.restapi.entity.RequestProcessing;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.repository.AuthCertRepository;
import org.niis.xroad.centralserver.restapi.repository.AuthenticationCertificateRegistrationRequestRepository;
import org.niis.xroad.centralserver.restapi.repository.IdentifierRepository;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.DataIntegrityException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.EnumSet;

import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.SUBMITTED_FOR_APPROVAL;
import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.WAITING;

/**
 * Service for handling authentication certificate registration requests
 */
@Service
@Transactional
@RequiredArgsConstructor
class AuthenticationCertificateRegistrationRequestHandler implements
        RequestHandler<AuthenticationCertificateRegistrationRequestDto, AuthenticationCertificateRegistrationRequest> {

    private final IdentifierRepository<SecurityServerId> identifiers;
    private final XRoadMemberRepository members;
    private final AuthenticationCertificateRegistrationRequestRepository authCertReqRequests;
    private final AuthCertRepository authCerts;
    private final SecurityServerRepository servers;

    /**
     * Creates an authentication certificate registration request.
     * In case automatic approval is enabled and prerequisites for approval are met,
     * the request is also fulfilled.
     * @param requestDto request to add
     * @return information about the added request
     * @throws ValidationFailureException if request is not acceptable
     * @throws DataIntegrityException if request violates data integrity rules
     */
    public AuthenticationCertificateRegistrationRequest add(
            AuthenticationCertificateRegistrationRequestDto requestDto) {

        if (Origin.CENTER.equals(requestDto.getOrigin())) {
            var owner = members.findOneBy(requestDto.getServerId().getOwner());
            if (owner.isEmpty()) {
                throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_OWNER_NOT_FOUND);
            }
        }

        final byte[] validatedCert;
        try {
            final X509Certificate authCert;
            authCert = CryptoUtils.readCertificate(requestDto.getAuthCert());
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

        var pendingRequests = authCertReqRequests.findByAuthCertAndStatus(validatedCert,
                EnumSet.of(WAITING, SUBMITTED_FOR_APPROVAL));

        AuthenticationCertificateRegistrationRequest request;
        switch (pendingRequests.size()) {
            case 0:
                request = newRequest(requestDto);
                break;
            case 1:
                var existingRequest = pendingRequests.get(0);
                if (!existingRequest.getOrigin().equals(requestDto.getOrigin())
                        && existingRequest.getSecurityServerId().equals(requestDto.getServerId())) {
                    request = complimentaryRequest(existingRequest, requestDto);
                    break;
                }
                throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_EXISTS,
                        String.valueOf(existingRequest.getId()));
            default:
                throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_EXISTS);
        }

        request.setAuthCert(validatedCert);
        request.setAddress(requestDto.getAddress());
        return authCertReqRequests.save(request);
    }

    public boolean canAutoApprove(AuthenticationCertificateRegistrationRequest request) {
        return SystemProperties.getCenterAutoApproveAuthCertRegRequests()
                && request.getOrigin() == Origin.SECURITY_SERVER
                && members.findOneBy(request.getSecurityServerId().getOwner()).isPresent();
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

        //check prerequisites (member exists)
        var owner = members.findOneBy(request.getSecurityServerId().getOwner())
                .orElseThrow(() -> new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_OWNER_NOT_FOUND));

        //create new security server if necessary
        final var serverCode = request.getSecurityServerId().getServerCode();
        var server = servers.findByOwnerAndServerCode(owner, serverCode)
                .orElseGet(() -> new SecurityServer(owner, serverCode));

        //register certificate
        server.getAuthCerts().add(new AuthCert(server, request.getAuthCert()));
        server.setAddress(request.getAddress());

        servers.save(server);
        request.getRequestProcessing().setStatus(ManagementRequestStatus.APPROVED);

        //todo: handle global group registration

        return request;
    }

    @Override
    public Class<AuthenticationCertificateRegistrationRequest> requestType() {
        return AuthenticationCertificateRegistrationRequest.class;
    }

    @Override
    public Class<AuthenticationCertificateRegistrationRequestDto> dtoType() {
        return AuthenticationCertificateRegistrationRequestDto.class;
    }

    private AuthenticationCertificateRegistrationRequest newRequest(
            AuthenticationCertificateRegistrationRequestDto requestDto) {

        var serverId = identifiers
                .findOne(Example.of(requestDto.getServerId()))
                .orElseGet(() -> identifiers.save(requestDto.getServerId()));

        var processing = new RequestProcessing();
        return new AuthenticationCertificateRegistrationRequest(requestDto.getOrigin(), serverId, processing);
    }

    private AuthenticationCertificateRegistrationRequest complimentaryRequest(
            AuthenticationCertificateRegistrationRequest existingRequest,
            AuthenticationCertificateRegistrationRequestDto request) {

        var processing = existingRequest.getRequestProcessing();
        processing.setStatus(SUBMITTED_FOR_APPROVAL);

        return new AuthenticationCertificateRegistrationRequest(
                request.getOrigin(),
                existingRequest.getSecurityServerId(),
                processing);
    }

}
