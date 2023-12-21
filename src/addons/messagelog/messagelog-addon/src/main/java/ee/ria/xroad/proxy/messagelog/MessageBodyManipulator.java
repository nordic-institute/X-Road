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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapBuilder;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.messagelog.LogMessage;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.RestLogMessage;
import ee.ria.xroad.common.messagelog.SoapLogMessage;

import com.google.common.collect.Iterables;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;

/**
 * Utility class for processing SoapMessages and removing altered message with <soap:body>
 * section removed.
 */
public class MessageBodyManipulator {

    /**
     * Extract configuration reading for better testability
     */
    public class Configurator {
        /**
         * Returns list of local producer subsystem ClientIds for which global SOAP body logging
         * setting is overridden
         * @return list of ClientId
         */
        public Collection<ClientId> getLocalProducerOverrides() {
            return MessageLogProperties.getMessageBodyLoggingLocalProducerOverrides();
        }

        /**
         * Returns list of remote producer subsystem ClientIds for which global SOAP body logging
         * setting is overridden
         * @return list of ClientId
         */
        public Collection<ClientId> getRemoteProducerOverrides() {
            return MessageLogProperties.getMessageBodyLoggingRemoteProducerOverrides();
        }

        /**
         * Tells whether SOAP body logging is enabled
         * @return true if enabled
         */
        public boolean isMessageBodyLoggingEnabled() {
            return MessageLogProperties.isMessageBodyLoggingEnabled();
        }
    }

    @Setter
    private Configurator configurator = new Configurator();

    /**
     * Returns the string that should be logged. This will either be the original soap message
     * (when soap:body logging is used for this message) or manipulated soap message with
     * soap:body element cleared.
     * @param message soap message
     * @return the string that should be logged
     * @throws Exception when error occurs
     */
    public String getLoggableMessageText(SoapLogMessage message) throws Exception {
        if (isBodyLogged(message)) {
            return message.getMessage().getXml();
        } else {
            return buildBodyRemovedMessage(message);
        }
    }

    /**
     * Returns the string that should be logged. This will either be the original message
     * filtered message with some elements removed
     */
    public String getLoggableMessageText(RestLogMessage message) {
        if (isBodyLogged(message)) {
            return new String(message.getMessage().getMessageBytes(), StandardCharsets.UTF_8);
        } else {
            return new String(message.getMessage().getFilteredMessage(), StandardCharsets.UTF_8);
        }
    }

    private String buildBodyRemovedMessage(SoapLogMessage message) throws Exception {
        // build a new empty message with SoapBuilder and
        // set old SoapHeader to it
        SoapHeader oldHeader = message.getMessage().getHeader();
        SoapBuilder builder = new SoapBuilder();
        builder.setHeader(oldHeader);
        builder.setRpcEncoded(false);
        SoapMessageImpl blankedMessage = builder.build();
        if (message.isResponse()) {
            // need to convert this to response message (changes body element name)
            // otherwise asicverifier gets confused
            blankedMessage = SoapUtils.toResponse(blankedMessage);
        }
        return blankedMessage.getXml();
    }

    /**
     * Tells whether SOAP body should be logged for this message.
     * @param message SOAP message
     * @return true if this message's body is logged
     */
    public boolean isBodyLogged(LogMessage message) {

        Collection<ClientId> overrides;
        if (message.isClientSide()) {
            overrides = configurator.getRemoteProducerOverrides();
        } else {
            overrides = configurator.getLocalProducerOverrides();
        }

        boolean producerSubsystemIsOverridden = isClientInCollection(message.getService().getClientId(), overrides);

        if (configurator.isMessageBodyLoggingEnabled()) {
            return !producerSubsystemIsOverridden;
        } else {
            return producerSubsystemIsOverridden;
        }
    }

    /**
     * Takes one ClientId object, and searches whether it is in searched group of ClientIds
     * @param searchParam ClientId to search
     * @param searched collection to search from
     * @return true if ClientId is in the collection
     */
    public boolean isClientInCollection(ClientId searchParam, Iterable<? extends ClientId> searched) {
        ClientId searchResult = Iterables.find(searched,
                input -> (input.memberEquals(searchParam)
                        && Objects.equals(input.getSubsystemCode(), searchParam.getSubsystemCode())), null);
        return (searchResult != null);
    }

}
