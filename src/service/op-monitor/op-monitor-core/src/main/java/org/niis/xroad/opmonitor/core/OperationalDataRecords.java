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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a payload of the request getSecurityServerOperationalData.
 */
@ToString
public class OperationalDataRecords {

    @Getter
    @JsonProperty("records")
    private List<OperationalDataRecord> records = new ArrayList<>();

    /**
     * Indicates queried records overflow if not null.
     */
    @Getter
    @Setter
    @JsonIgnore
    private Long nextRecordsFrom = null;

    @JsonCreator
    OperationalDataRecords(@JsonProperty("records") List<OperationalDataRecord> records) {
        this.records = records;
    }

    int size() {
        return records.size();
    }

    void append(OperationalDataRecords operationalDataRecords) {
        records.addAll(operationalDataRecords.getRecords());
    }

    void removeRecordsByMonitoringDataTs(long monitoringDataTs) {
        records.removeIf(i -> i.getMonitoringDataTs() == monitoringDataTs);
    }

    Long getLastMonitoringDataTs() {
        return records.isEmpty() ? null : records.getLast().getMonitoringDataTs();
    }

    String getPayload(ObjectWriter objectWriter) throws JsonProcessingException {
        return objectWriter.writeValueAsString(this);
    }
}
