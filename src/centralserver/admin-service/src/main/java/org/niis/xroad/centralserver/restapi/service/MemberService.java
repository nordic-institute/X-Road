/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;

import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClientView;
import org.niis.xroad.centralserver.restapi.entity.Subsystem;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerClientNameRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.EntityExistsException;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_EXISTS;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_NOT_FOUND;

/**
 * Service for searching {@link FlattenedSecurityServerClientView}s
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

    private final XRoadMemberRepository xRoadMemberRepository;
    private final SecurityServerClientNameRepository securityServerClientNameRepository;

    public XRoadMember add(XRoadMember client) {
        Consumer<XRoadMember> ensureClientNotExists = __ -> {
            boolean exists = xRoadMemberRepository.findOneBy(client.getIdentifier()).isDefined();
            if (exists) {
                throw new EntityExistsException(MEMBER_EXISTS, client.getIdentifier().toShortString());
            }
        };

        return Try.success(client)
                .andThen(ensureClientNotExists)
                .map(xRoadMemberRepository::save)
                .get();
    }

    public void delete(ClientId clientId) {
        XRoadMember member = xRoadMemberRepository.findMember(clientId)
                .getOrElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND));
        xRoadMemberRepository.delete(member);
    }

    public Option<XRoadMember> findMember(ClientId clientId) {
        return xRoadMemberRepository.findMember(clientId);
    }


    public Option<XRoadMember> updateMemberName(ClientId clientId, String newName) {
        return xRoadMemberRepository.findMember(clientId)
                .peek(xRoadMember -> updateName(xRoadMember, newName));
    }

    private void updateName(XRoadMember xRoadMember, String newName) {
        xRoadMember.setName(newName);

        Set<ClientId> identifiers = new HashSet<>();
        identifiers.add(xRoadMember.getIdentifier());
        xRoadMember.getSubsystems().stream()
                .map(Subsystem::getIdentifier)
                .forEach(identifiers::add);

        securityServerClientNameRepository.findByIdentifierIn(identifiers)
                .forEach(x -> x.setName(newName));

    }

}
