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
package ee.ria.xroad.proxy.common;

import ee.ria.xroad.common.identifier.ClientId;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Contains constants for message room properties.
 */
public final class MessageRoomProperties {

    private static final String PREFIX = "xroad.message-room.";

    public static final int NUM_COMPONENTS = 4;
    public static final int FIRST_COMPONENT = 0;
    public static final int SECOND_COMPONENT = 1;
    public static final int THIRD_COMPONENT = 2;
    public static final int FOURTH_COMPONENT = 3;

    private MessageRoomProperties() { }

    /**
     * Property name for listing enabled publisher subsystems
     **/
    public static final String ENABLED_PUBLISHER_SUBSYSTEMS = PREFIX + "enabled-publisher-subsystems";

    /**
     * Returns list of subsystem ClientIds that are enabled to act as Message Room publishers.
     *
     * @return list of ClientId.
     */
    public static Collection<ClientId> getEnabledPublisherSubsystems() {
        return parseClientIdParameters(System.getProperty(ENABLED_PUBLISHER_SUBSYSTEMS, ""));
    }

    /**
     * Given one parameter parses it to collection of ClientIds. Parameter should be of format
     * NIIS/GOV/1710128-9/Client1, NIIS/GOV/1710128-9/Client2, that is: comma separated list of
     * slash-separated subsystem identifiers.
     *
     * @param clientIdParameters
     * @return
     */
    private static Collection<ClientId> parseClientIdParameters(String clientIdParameters) {
        Collection<ClientId> toReturn = new ArrayList<>();
        Iterable<String> splitSubsystemParams = Splitter.on(",")
                .trimResults()
                .omitEmptyStrings()
                .split(clientIdParameters);

        Splitter codeSplitter = Splitter.on("/").trimResults();

        for (String oneSubsystemParam : splitSubsystemParams) {
            List<String> codes = Lists.newArrayList(codeSplitter.split(oneSubsystemParam));

            if (codes.size() != NUM_COMPONENTS) {
                throw new IllegalStateException("Enabled publisher subsystems parameter should be comma-separated "
                        + "list of four slash-separated codes identifying one subsystem, for example "
                        + "\"NIIS/ORG/1234567-1/subsystem1\", detected bad value: " + oneSubsystemParam);
            }
            ClientId id = ClientId.create(codes.get(FIRST_COMPONENT), codes.get(SECOND_COMPONENT),
                    codes.get(THIRD_COMPONENT), codes.get(FOURTH_COMPONENT));
            toReturn.add(id);
        }

        return toReturn;
    }
}
