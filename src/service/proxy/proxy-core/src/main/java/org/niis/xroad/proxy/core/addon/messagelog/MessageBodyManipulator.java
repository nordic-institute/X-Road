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
package org.niis.xroad.proxy.core.addon.messagelog;

import ee.ria.xroad.common.SystemPropertySource;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapBuilder;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.messagelog.LogMessage;
import ee.ria.xroad.common.messagelog.RestLogMessage;
import ee.ria.xroad.common.messagelog.SoapLogMessage;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPException;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.niis.xroad.proxy.core.configuration.ProxyMessageLogProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for processing SoapMessages and removing altered message with <soap:body>
 * section removed.
 */
@RequiredArgsConstructor
public class MessageBodyManipulator {
    private final ProxyMessageLogProperties messageLogProperties;

    /**
     * Extract configuration reading for better testability
     */
    public class Configurator {
        private static final int NUM_COMPONENTS = 4;
        private static final int FIRST_COMPONENT = 0;
        private static final int SECOND_COMPONENT = 1;
        private static final int THIRD_COMPONENT = 2;
        private static final int FOURTH_COMPONENT = 3;

        private static final String PREFIX = "xroad.message-log.";
        /**
         * Property name for toggling message body logging on/off
         **/
        public static final String MESSAGE_BODY_LOGGING_ENABLED = PREFIX + "message-body-logging";

        /**
         * Prefix for enable-overriding message body logging
         **/
        private static final String MESSAGE_BODY_LOGGING_ENABLE = PREFIX + "enabled-body-logging";

        /**
         * Prefix for disable-overriding message body logging
         **/
        private static final String MESSAGE_BODY_LOGGING_DISABLE = PREFIX + "disabled-body-logging";

        /**
         * Postfix for overriding message body logging for local producers
         **/
        private static final String MESSAGE_BODY_LOGGING_LOCAL_PRODUCER = "-local-producer-subsystems";

        /**
         * Postfix for overriding message body logging for remote producers
         **/
        private static final String MESSAGE_BODY_LOGGING_REMOTE_PRODUCER = "-remote-producer-subsystems";

        /**
         * Returns list of local producer subsystem ClientIds for which global SOAP body logging
         * setting is overridden
         *
         * @return list of ClientId
         */
        public Collection<ClientId> getLocalProducerOverrides() {
            return getMessageBodyLoggingOverrides(true);
        }

        /**
         * Returns list of remote producer subsystem ClientIds for which global SOAP body logging
         * setting is overridden
         *
         * @return list of ClientId
         */
        public Collection<ClientId> getRemoteProducerOverrides() {
            return getMessageBodyLoggingOverrides(false);
        }

        /**
         * Tells whether SOAP body logging is enabled
         *
         * @return true if enabled
         */
        public boolean isMessageBodyLoggingEnabled() {
            return messageLogProperties.messageBodyLogging();
        }

        private Collection<ClientId> getMessageBodyLoggingOverrides(boolean local) {
            validateBodyLoggingOverrideParameters();

            return parseClientIdParameters(getMessageBodyLoggingOverrideParameter(!isMessageBodyLoggingEnabled(), local));
        }

        /**
         * Check that "enableBodyLogging..." parameters are not used if body logging is toggled on, and vice versa.
         */
        private void validateBodyLoggingOverrideParameters() {
            boolean checkEnableOverrides = isMessageBodyLoggingEnabled();

            validateBodyLoggingOverrideParamNotUsed(checkEnableOverrides, true);
            validateBodyLoggingOverrideParamNotUsed(checkEnableOverrides, false);
        }

        /**
         * Check that given parameter is not in use, and if it is throws IllegalStateException.
         *
         * @param enable
         * @param local
         */
        private void validateBodyLoggingOverrideParamNotUsed(boolean enable, boolean local) {
            if (!getMessageBodyLoggingOverrideParameter(enable, local).isEmpty()) {
                throw new IllegalStateException(getMessageBodyLoggingOverrideParameterName(enable, local)
                        + " should not be used when " + MESSAGE_BODY_LOGGING_ENABLED
                        + " is " + isMessageBodyLoggingEnabled());
            }
        }

        private String getMessageBodyLoggingOverrideParameterName(boolean enable, boolean local) {
            String prefix = enable ? MESSAGE_BODY_LOGGING_ENABLE : MESSAGE_BODY_LOGGING_DISABLE;
            String postfix = local ? MESSAGE_BODY_LOGGING_LOCAL_PRODUCER : MESSAGE_BODY_LOGGING_REMOTE_PRODUCER;

            return prefix + postfix;
        }

        private static String getProperty(String key, String defaultValue) {
            return SystemPropertySource.getPropertyResolver().getProperty(key, defaultValue);
        }

        private String getMessageBodyLoggingOverrideParameter(boolean enable, boolean local) {
            return getProperty(getMessageBodyLoggingOverrideParameterName(enable, local), "");
        }

        /**
         * Given one parameter parses it to collection of ClientIds. Parameter should be of format
         * FI/GOV/1710128-9/MANSIKKA, FI/GOV/1710128-9/MUSTIKKA, that is: comma separated list of slash-separated subsystem
         * identifiers.
         *
         * @param clientIdParameters
         * @return
         */
        private Collection<ClientId> parseClientIdParameters(String clientIdParameters) {
            Collection<ClientId> toReturn = new ArrayList<>();
            Iterable<String> splitSubsystemParams = Splitter.on(",")
                    .trimResults()
                    .omitEmptyStrings()
                    .split(clientIdParameters);

            Splitter codeSplitter = Splitter.on("/").trimResults();

            for (String oneSubsystemParam : splitSubsystemParams) {
                List<String> codes = Lists.newArrayList(codeSplitter.split(oneSubsystemParam));

                if (codes.size() != NUM_COMPONENTS) {
                    throw new IllegalStateException("Message body logging override parameter should be comma-separated "
                            + "list of four slash-separated codesidentifying one subsystem, for example "
                            + "\"FI/ORG/1234567-1/subsystem1\", detected bad value: " + oneSubsystemParam);
                }
                ClientId id = ClientId.Conf.create(codes.get(FIRST_COMPONENT), codes.get(SECOND_COMPONENT),
                        codes.get(THIRD_COMPONENT), codes.get(FOURTH_COMPONENT));
                toReturn.add(id);
            }

            return toReturn;
        }
    }

    @Setter
    private Configurator configurator = new Configurator();

    /**
     * Returns the string that should be logged. This will either be the original soap message
     * (when soap:body logging is used for this message) or manipulated soap message with
     * soap:body element cleared.
     *
     * @param message soap message
     * @return the string that should be logged
     * @throws Exception when error occurs
     */
    public String getLoggableMessageText(SoapLogMessage message) throws IOException, SOAPException, JAXBException, IllegalAccessException {
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

    private String buildBodyRemovedMessage(SoapLogMessage message)
            throws SOAPException, JAXBException, IllegalAccessException, IOException {
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
     *
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
     *
     * @param searchParam ClientId to search
     * @param searched    collection to search from
     * @return true if ClientId is in the collection
     */
    public boolean isClientInCollection(ClientId searchParam, Iterable<? extends ClientId> searched) {
        ClientId searchResult = Iterables.find(searched,
                input -> (input.memberEquals(searchParam)
                        && Objects.equals(input.getSubsystemCode(), searchParam.getSubsystemCode())), null);
        return (searchResult != null);
    }

}
