/**
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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.monitoringconf.MonitoringConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.MultipartSoapMessageEncoder;
import ee.ria.xroad.common.message.SoapMessageEncoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.opmonitordaemon.message.GetSecurityServerOperationalDataResponseType;
import ee.ria.xroad.opmonitordaemon.message.GetSecurityServerOperationalDataType;
import ee.ria.xroad.opmonitordaemon.message.SearchCriteriaType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.opmonitordaemon.OperationalDataOutputSpecFields.OUTPUT_FIELDS;

/**
 * Query handler for operation data requests.
 */
@Slf4j
@RequiredArgsConstructor
class OperationalDataRequestHandler extends QueryRequestHandler {

    private static final int OFFSET_SECONDS =
            OpMonitoringSystemProperties.
                    getOpMonitorRecordsAvailableTimestampOffsetSeconds();

    protected static final String CID = "operational-monitoring-data.json.gz";

    @Override
    public void handle(SoapMessageImpl requestSoap, OutputStream out,
            Consumer<String> contentTypeCallback) throws Exception {
        log.trace("handle()");

        ClientId clientId = requestSoap.getClient();
        SecurityServerId serverId = requestSoap.getSecurityServer();

        GetSecurityServerOperationalDataType requestData = getRequestData(
                requestSoap, GetSecurityServerOperationalDataType.class);

        SearchCriteriaType searchCriteria = requestData.getSearchCriteria();
        long recordsFrom = searchCriteria.getRecordsFrom();
        long recordsTo = searchCriteria.getRecordsTo();
        long recordsAvailableBefore = getRecordsAvailableBeforeTimestamp();

        checkTimestamps(recordsFrom, recordsTo, recordsAvailableBefore);

        ClientId serviceProviderId = searchCriteria.getClient();
        Set<String> outputFields = requestData.getOutputSpec() == null
                ? new HashSet<>()
                : new HashSet<>(requestData.getOutputSpec().getOutputField());

        checkOutputFields(outputFields);

        log.debug("Handle getSecurityServerOperationalData: clientId: {},"
                        + " recordsFrom: {}, recordsTo: {},"
                        + " serviceProviderId: {}, outputFields: {}",
                clientId, recordsFrom, recordsTo, serviceProviderId,
                outputFields);

        GetSecurityServerOperationalDataResponseType opDataResponse =
                buildOperationalDataResponse(
                        getClientForFilter(clientId, serverId), recordsFrom,
                        recordsTo, serviceProviderId, outputFields,
                        recordsAvailableBefore);

        try (SoapMessageEncoder responseEncoder = new MultipartSoapMessageEncoder(out)) {
            contentTypeCallback.accept(responseEncoder.getContentType());

            SoapEncoderAttachmentMarshaller attachmentMarshaller =
                    new SoapEncoderAttachmentMarshaller(responseEncoder);
            Marshaller marshaller = createMarshaller(attachmentMarshaller);

            SoapMessageImpl response = createResponse(requestSoap, marshaller,
                    createResponseElement(opDataResponse));
            responseEncoder.soap(response, new HashMap<>());

            attachmentMarshaller.encodeAttachments();
        }
    }

    static void checkTimestamps(long recordsFrom, long recordsTo,
            long recordsAvailableBefore) {
        if (recordsFrom < 0) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Records from timestamp is a negative number")
                    .withPrefix(CLIENT_X);
        }

        if (recordsTo < 0) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Records to timestamp is a negative number")
                    .withPrefix(CLIENT_X);
        }

        if (recordsTo < recordsFrom) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Records to timestamp is earlier than records from"
                            + " timestamp").withPrefix(CLIENT_X);
        }

        if (recordsFrom >= recordsAvailableBefore) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Records not available from " + recordsFrom + " yet")
                    .withPrefix(CLIENT_X);
        }
    }

    protected static void checkOutputFields(Set<String> outputFields) {
        for (String field : outputFields) {
            if (!OUTPUT_FIELDS.contains(field)) {
                throw new CodedException(X_INVALID_REQUEST,
                        "Unknown output field in search criteria: " + field)
                        .withPrefix(CLIENT_X);
            }
        }
    }

    protected GetSecurityServerOperationalDataResponseType
            buildOperationalDataResponse(ClientId filterByClient,
            long recordsFrom, long recordsTo, ClientId filterByServiceProvider,
            Set<String> outputFields, long recordsAvailableBefore)
            throws IOException {
        OperationalDataRecords responseRecords;
        GetSecurityServerOperationalDataResponseType opDataResponse =
                OBJECT_FACTORY
                        .createGetSecurityServerOperationalDataResponseType();

        if (recordsTo >= recordsAvailableBefore) {
            log.debug("recordsTo({}) >= recordsAvailableBefore({}),"
                            + " set nextRecordsFrom to {}", recordsTo,
                    recordsAvailableBefore, recordsAvailableBefore);

            recordsTo = recordsAvailableBefore - 1;

            opDataResponse.setNextRecordsFrom(recordsAvailableBefore);
        }

        responseRecords = getOperationalDataRecords(filterByClient,
                recordsFrom, recordsTo, filterByServiceProvider, outputFields);

        opDataResponse.setRecordsCount(responseRecords.size());
        String payload = responseRecords.getPayload(OBJECT_WRITER);

        // Optimize memory usage: release records memory before compressing
        // operation.
        responseRecords.getRecords().clear();

        opDataResponse.setRecords(createAttachmentDataSource(compress(payload),
                CID, MimeTypes.GZIP));

        if (responseRecords.getNextRecordsFrom() != null) {
            opDataResponse.setNextRecordsFrom(
                    responseRecords.getNextRecordsFrom());
        }

        return opDataResponse;
    }

    private static JAXBElement<?> createResponseElement(
            GetSecurityServerOperationalDataResponseType opDataResponse) {
        return OBJECT_FACTORY.createGetSecurityServerOperationalDataResponse(
                opDataResponse);
    }

    protected OperationalDataRecords getOperationalDataRecords(
            ClientId filterByClient, long recordsFrom, long recordsTo,
            ClientId filterByServiceProvider, Set<String> outputFields) {
        try {
            return OperationalDataRecordManager.queryRecords(recordsFrom,
                    recordsTo, filterByClient, filterByServiceProvider,
                    outputFields);
        } catch (Exception e) {
            log.error("Failed to get records for response", e);

            throw new CodedException(X_INTERNAL_ERROR,
                    "Failed to get records for response: " + e.getMessage());
        }
    }

    protected ClientId getClientForFilter(ClientId clientId,
            SecurityServerId serverId) throws Exception {
        return !isMonitoringClient(clientId)
                && !isServerOwner(clientId, serverId) ? clientId : null;
    }

    private boolean isMonitoringClient(ClientId clientId) {
        return clientId != null && clientId.equals(
                MonitoringConf.getInstance().getMonitoringClient());
    }

    private boolean isServerOwner(ClientId clientId, SecurityServerId serverId)
            throws Exception {
        return serverId != null
                && clientId.equals(GlobalConf.getServerOwner(serverId));
    }

    private static long getRecordsAvailableBeforeTimestamp() {
        return TimeUtils.getEpochSecond() - OFFSET_SECONDS;
    }
}
