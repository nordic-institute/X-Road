/*
 * The MIT License
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
package ee.ria.xroad.messagelog.database.mapper;

import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.messagelog.database.entity.AbstractLogRecordEntity;
import ee.ria.xroad.messagelog.database.entity.MessageRecordEntity;
import ee.ria.xroad.messagelog.database.entity.TimestampRecordEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.mapstruct.SubclassExhaustiveStrategy.RUNTIME_EXCEPTION;

@Mapper(subclassExhaustiveStrategy = RUNTIME_EXCEPTION)
public interface MessageRecordMapper {


    default AbstractLogRecordEntity toEntity(LogRecord source) {
        return switch (source) {
            case null -> null;
            case MessageRecord mr -> toEntity(mr);
            case TimestampRecord tr -> toEntity(tr);
            default -> throw new IllegalArgumentException("Unsupported log record type: " + source.getId());
        };
    }

    default LogRecord toDTO(AbstractLogRecordEntity source) {
        return switch (source) {
            case null -> null;
            case MessageRecordEntity mr -> toDTO(mr);
            case TimestampRecordEntity tr -> toDTO(tr);
            default -> throw new IllegalArgumentException("Unsupported log record entity type: " + source.getId());
        };
    }

    @Mapping(target = "messageCipher", ignore = true)
    @Mapping(target = "attachmentCipher", ignore = true)
    MessageRecord toDTO(MessageRecordEntity source);

    TimestampRecord toDTO(TimestampRecordEntity source);

    List<MessageRecord> toDTOs(List<MessageRecordEntity> sources);

    MessageRecordEntity toEntity(MessageRecord source);

    TimestampRecordEntity toEntity(TimestampRecord source);

    MessageRecordMapper INSTANCE = Mappers.getMapper(MessageRecordMapper.class);

    static MessageRecordMapper get() {
        return INSTANCE;
    }
}
