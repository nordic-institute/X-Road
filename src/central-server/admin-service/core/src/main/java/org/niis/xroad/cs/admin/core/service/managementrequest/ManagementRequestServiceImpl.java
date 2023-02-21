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
package org.niis.xroad.cs.admin.core.service.managementrequest;

import io.vavr.collection.Stream;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestView;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.Request;
import org.niis.xroad.cs.admin.api.dto.ManagementRequestInfoDto;
import org.niis.xroad.cs.admin.api.exception.DataIntegrityException;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.api.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.exception.UncheckedServiceException;
import org.niis.xroad.cs.admin.api.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.core.entity.RequestEntity;
import org.niis.xroad.cs.admin.core.entity.RequestWithProcessingEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ManagementRequestViewMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.ManagementRequestViewRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Implements generic management request services that do not depend on the request type.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ManagementRequestServiceImpl implements ManagementRequestService {
    private final RequestRepository<RequestEntity> requests;
    private final ManagementRequestViewRepository managementRequestViewRepository;
    private final List<RequestHandler<? extends Request>> handlers;
    private final RequestMapper requestMapper;
    private final ManagementRequestViewMapper viewMapper;

    /**
     * Get a management request
     *
     * @param id request id
     */
    @Override
    public Optional<Request> getRequest(int id) {
        return requests.findById(id)
                .map(requestMapper::toTarget);
    }

    @Override
    public Optional<ManagementRequestView> getRequestView(int requestId) {
        return managementRequestViewRepository.findById(requestId)
                .map(viewMapper::toTarget);
    }

    @Override
    public ManagementRequestType getRequestType(int id) {
        return requests.findById(id)
                .map(RequestEntity::getManagementRequestType)
                .orElseThrow(() -> new NotFoundException(ErrorMessage.MANAGEMENT_REQUEST_NOT_FOUND));
    }

    /**
     * Find management requests matching criteria.
     */
    @Override
    public Page<ManagementRequestInfoDto> findRequests(
            ManagementRequestService.Criteria filter,
            Pageable page) {
        var result = managementRequestViewRepository.findAll(filter, page);
        return result.map(ManagementRequests::asInfoDto);
    }

    /**
     * Add new management request
     */
    @Override
    public <T extends Request> T add(T request) {
        return dispatch(handler -> this.doAdd(handler, request));
    }

    /**
     * Approve pending management request
     *
     * @param requestId request id to approve
     */
    @Override
    public <T extends Request> T approve(int requestId) {
        var request = findRequest(requestId);
        return (T) dispatch(handler -> this.doApprove(handler, requestMapper.toTarget(request)));
    }

    /**
     * Revoke (decline) pending management request
     *
     * @param requestId request id to revoke
     */
    @Override
    public void revoke(Integer requestId) {
        var request = findRequest(requestId);
        if (!EnumSet.of(ManagementRequestStatus.WAITING, ManagementRequestStatus.SUBMITTED_FOR_APPROVAL)
                .contains(request.getProcessingStatus())) {
            throw new ValidationFailureException(ErrorMessage.MANAGEMENT_REQUEST_INVALID_STATE);
        }

        if (request instanceof RequestWithProcessingEntity) {
            var processing = ((RequestWithProcessingEntity) request).getRequestProcessing();
            if (processing.getRequests().size() == 1 && request.getOrigin() == Origin.CENTER) {
                processing.setStatus(ManagementRequestStatus.REVOKED);
            } else {
                processing.setStatus(ManagementRequestStatus.DECLINED);
            }
            //TODO persist?
        } else {
            // should not happen since simple requests can not be pending
            throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_NOT_SUPPORTED);
        }
    }

    private <T extends RequestEntity> T findRequest(int requestId) {
        return (T) requests.findById(requestId)
                .orElseThrow(() -> new NotFoundException(ErrorMessage.MANAGEMENT_REQUEST_NOT_FOUND));
    }

    /*
     * Dispatches request to handlers, returns the response of the first
     * handler that can handle the request.
     */
    private <T extends Request> T dispatch(Function<RequestHandler<Request>, Option<T>> operation) {
        return Stream.ofAll(handlers)
                .map(handler -> (RequestHandler<Request>) handler)
                .map(operation)
                .filter(Option::isDefined)
                .map(Option::get)
                .headOption()
                .getOrElseThrow(() -> {
                    throw new UncheckedServiceException(ErrorMessage.MANAGEMENT_REQUEST_NOT_SUPPORTED);
                });
    }

    /*
     * Some generics wrangling to work around type erasure,
     * and to refine wildcards to type parameters.
     */
    private <T extends Request> Option<T> doApprove(RequestHandler<Request> handler, T request) {
        return handler.narrow(request).map(handler::approve).map(r -> (T) r);
    }

    private <T extends Request> Option<T> doAdd(RequestHandler<Request> handler, T request) {
        return handler.narrow(request).map(r -> {
            T response = (T) handler.add((T) r);
            if (handler.canAutoApprove(response)) {
                response = (T) handler.approve(response);
            }
            return response;
        });
    }
}
