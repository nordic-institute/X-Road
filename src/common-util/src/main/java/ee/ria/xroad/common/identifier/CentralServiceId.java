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
package ee.ria.xroad.common.identifier;

import io.vavr.control.Option;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import static ee.ria.xroad.common.util.Validation.validateArgument;

/**
 * Central service ID.
 */
public interface CentralServiceId extends ServiceId {

    default String[] getFieldsForStringFormat() {
        return new String[]{getServiceCode()};
    }

    // todo: move to a proper location
    @XmlJavaTypeAdapter(IdentifierTypeConverter.CentralServiceIdAdapter.class)
    final class Conf extends ServiceId.Conf implements CentralServiceId {

        Conf() { // required by Hibernate
            this(null, null);
        }

        private Conf(String xRoadInstance, String serviceCode) {
            super(XRoadObjectType.CENTRALSERVICE, xRoadInstance, null, null,
                    null, serviceCode);
        }

        /**
         * Factory method for creating a new CentralServiceId.
         *
         * @param xRoadInstance instance of the new service
         * @param serviceCode   code if the new service
         * @return CentralServiceId
         */
        public static CentralServiceId.Conf create(String xRoadInstance,
                                                   String serviceCode) {
            validateArgument("xRoadInstance", xRoadInstance);
            validateArgument("serviceCode", serviceCode);
            return new CentralServiceId.Conf(xRoadInstance, serviceCode);
        }

        public static CentralServiceId.Conf ensure(CentralServiceId identifier) {
            validateArgument("identifier", identifier);
            return Option.of(identifier)
                    .filter(CentralServiceId.Conf.class::isInstance)
                    .map(CentralServiceId.Conf.class::cast)
                    .getOrElse(() -> new CentralServiceId.Conf(identifier.getXRoadInstance(),
                            identifier.getServiceCode()));
        }

    }

}
