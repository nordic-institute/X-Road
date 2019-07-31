/**
 * The MIT License
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
package ee.ria.xroad.common.identifier;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Service ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.ServiceIdAdapter.class)
public class ServiceId extends XRoadId {

    private final String memberClass;
    private final String memberCode;
    private final String serviceVersion;
    private final String subsystemCode;
    protected final String serviceCode;

    ServiceId() { // required by Hibernate
        this(null, null, null, null, null, null);
    }

    protected ServiceId(XRoadObjectType type, String xRoadInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode) {
        this(type, xRoadInstance, memberClass, memberCode, subsystemCode,
                serviceCode, null);
    }

    protected ServiceId(XRoadObjectType type, String xRoadInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode, String serviceVersion) {
        super(type, xRoadInstance);

        this.memberClass = memberClass;
        this.memberCode = memberCode;
        this.serviceVersion = serviceVersion;
        this.subsystemCode = subsystemCode;
        this.serviceCode = serviceCode;
    }

    /**
     * Returns the member class of the service provider.
     * @return String
     */
    public String getMemberClass() {
        return memberClass;
    }

    /**
     * Returns the member code of the service provider.
     * @return String
     */
    public String getMemberCode() {
        return memberCode;
    }

    /**
     * Returns subsystem code, if present, or null otherwise.
     * @return String or null
     */
    public String getSubsystemCode() {
        return subsystemCode;
    }

    /**
     * Returns the service version.
     * @return String
     */
    public String getServiceVersion() {
        return serviceVersion;
    }

    /**
     * Returns the service code.
     * @return String
     */
    public String getServiceCode() {
        return serviceCode;
    }

    /**
     * Returns the provider client ID of this service.
     * @return ClientId
     */
    @JsonIgnore
    public ClientId getClientId() {
        return ClientId.create(getXRoadInstance(),
                memberClass, memberCode, subsystemCode);
    }

    @Override
    public String[] getFieldsForStringFormat() {
        return new String[] {
                memberClass, memberCode, subsystemCode, serviceCode,
                serviceVersion };
    }

    /**
     * Factory method for creating a new ServiceId.
     * @param client ID of the service provider
     * @param serviceCode code of the new service
     * @return ServiceId
     */
    public static ServiceId create(ClientId client, String serviceCode) {
        return create(client.getXRoadInstance(), client.getMemberClass(),
                client.getMemberCode(), client.getSubsystemCode(), serviceCode);
    }

    /**
     * Factory method for creating a new ServiceId.
     * @param client ID of the service provider
     * @param serviceCode code of the new service
     * @param serviceVersion version of the new service
     * @return ServiceId
     */
    public static ServiceId create(ClientId client, String serviceCode,
            String serviceVersion) {
        return create(client.getXRoadInstance(), client.getMemberClass(),
                client.getMemberCode(), client.getSubsystemCode(), serviceCode,
                serviceVersion);
    }

    /**
     * Factory method for creating a new ServiceId.
     * @param xRoadInstance instance of the service provider
     * @param memberClass class of the service provider
     * @param memberCode code of the service provider
     * @param subsystemCode subsystem code of the service provider
     * @param serviceCode code of the new service
     * @return ServiceId
     */
    public static ServiceId create(String xRoadInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode) {
        return create(xRoadInstance, memberClass, memberCode, subsystemCode,
                serviceCode, null);
    }

    /**
     * Factory method for creating a new ServiceId.
     * @param xRoadInstance instance of the service provider
     * @param memberClass class of the service provider
     * @param memberCode code of the service provider
     * @param subsystemCode subsystem code of the service provider
     * @param serviceCode code of the new service
     * @param serviceVersion version of the new service
     * @return ServiceId
     */
    public static ServiceId create(String xRoadInstance,
            String memberClass, String memberCode, String subsystemCode,
            String serviceCode, String serviceVersion) {
        validateField("xRoadInstance", xRoadInstance);
        validateField("memberClass", memberClass);
        validateField("memberCode", memberCode);
        validateField("serviceCode", serviceCode);
        return new ServiceId(XRoadObjectType.SERVICE, xRoadInstance, memberClass,
                memberCode, subsystemCode, serviceCode, serviceVersion);
    }
}
