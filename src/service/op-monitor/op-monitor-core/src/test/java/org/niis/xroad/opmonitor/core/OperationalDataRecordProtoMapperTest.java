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
package org.niis.xroad.opmonitor.core;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.api.StoreOperationalDataReq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.PRODUCER;

class OperationalDataRecordProtoMapperTest {

    @Test
    void recordWithOnlyRequiredFieldsMapsLikeRestStorePath() {
        var data = new OpMonitoringData(CLIENT, 1_700_000_000_000L);
        data.setSecurityServerInternalIp("10.0.0.1");
        data.setResponseOutTs(1_700_000_000_500L, false);

        assertThat(viaProto(data)).isEqualTo(viaJson(data));
    }

    @Test
    void recordWithEveryFieldSetMapsLikeRestStorePath() {
        var data = OpMonitoringDataFixtures.fullyPopulated(PRODUCER);

        var record = viaProto(data);

        assertThat(record).isEqualTo(viaJson(data));
        assertThat(record).hasNoNullFieldsOrPropertiesExcept("id", "monitoringDataTs");
        assertThat(record.getXRequestId()).isEqualTo("8a2f2e10-8ffb-40c3-b446-aebd23fd9248");
    }

    @Test
    void everyStoreRecordFieldHasAProtoField() {
        var data = OpMonitoringDataFixtures.fullyPopulated(CLIENT);
        int protoFieldCount = OperationalDataRecordProto.getDescriptor().getFields().size();

        assertThat(data.getData()).hasSize(protoFieldCount);
        assertThat(data.toProto().getAllFields()).hasSize(protoFieldCount);
        assertThat(viaProto(data)).isEqualTo(viaJson(data));
    }

    @Test
    void nullValueIsLeftUnsetLikeRestStorePath() {
        var data = new OpMonitoringData(PRODUCER, 1_700_000_000_000L);
        data.setSecurityServerInternalIp("10.0.0.1");
        data.setResponseOutTs(1_700_000_000_500L, false);
        data.setXRequestId(null);

        assertThat(data.toProto().hasXRequestId()).isFalse();
        assertThat(viaProto(data)).isEqualTo(viaJson(data));
    }

    @Test
    void textIsTruncatedToPersistenceLimitsBeforeSerialization() {
        var proto = OpMonitoringDataFixtures.withOversizedText(PRODUCER).toProto();

        assertThat(proto.getMessageUserId()).hasSize(255);
        assertThat(proto.getServiceCode()).hasSize(255);
        assertThat(proto.getFaultString()).hasSize(2048);
        assertThat(proto.getSerializedSize()).isLessThan(26 * 1024);
    }

    @Test
    void worstCaseTruncatedRecordFitsTheSmallestAllowedServerInboundLimit() {
        var data = OpMonitoringDataFixtures.withOversizedText(PRODUCER);
        data.setMessageUserId("\u20ac".repeat(5 * 1024 * 1024));
        var request = StoreOperationalDataReq.newBuilder().addRecords(data.toProto()).build();

        assertThat(request.getSerializedSize()).isLessThan(OpMonitorConfigKeys.MIN_STORE_MESSAGE_SIZE / 2);
    }

    @Test
    void emptyMessageMapsToRecordWithoutValues() {
        var record = OperationalDataRecordProtoMapper.fromProto(OperationalDataRecordProto.getDefaultInstance());

        assertThat(record).hasAllNullFieldsOrProperties();
    }

    private static OperationalDataRecord viaProto(OpMonitoringData data) {
        return OperationalDataRecordProtoMapper.fromProto(data.toProto());
    }

    private static OperationalDataRecord viaJson(OpMonitoringData data) {
        return OpMonitoringDataFixtures.viaRestJson(data);
    }
}
