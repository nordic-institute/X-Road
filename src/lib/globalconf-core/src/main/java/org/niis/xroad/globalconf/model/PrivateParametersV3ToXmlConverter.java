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
package org.niis.xroad.globalconf.model;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import org.niis.xroad.globalconf.schema.privateparameters.v3.ConfigurationAnchorType;
import org.niis.xroad.globalconf.schema.privateparameters.v3.ConfigurationSourceType;
import org.niis.xroad.globalconf.schema.privateparameters.v3.ObjectFactory;
import org.niis.xroad.globalconf.schema.privateparameters.v3.PrivateParametersTypeV3;

@Mapper(uses = {ObjectFactory.class, MappingUtils.class},
        unmappedTargetPolicy = ReportingPolicy.ERROR)
interface PrivateParametersV3ToXmlConverter {

    PrivateParametersV3ToXmlConverter INSTANCE = Mappers.getMapper(PrivateParametersV3ToXmlConverter.class);

    @Mapping(source = "configurationAnchors", target = "configurationAnchor")
    @Mapping(target = "any", ignore = true)
    PrivateParametersTypeV3 convert(PrivateParameters parameters);

    @Mapping(source = "sources", target = "source")
    ConfigurationAnchorType convertAnchor(PrivateParameters.ConfigurationAnchor configurationAnchor);

    @Mapping(source = "verificationCerts", target = "verificationCert")
    ConfigurationSourceType convertSource(PrivateParameters.Source configurationSource);
}
