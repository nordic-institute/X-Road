/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v1.ManagementServiceType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v1.ObjectFactory;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v1.PrivateParametersType;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains private parameters of a configuration instance.
 */
public class PrivateParametersV1 extends AbstractXmlConf<PrivateParametersType> {

  PrivateParametersV1() {
    super(ObjectFactory.class, PrivateParametersSchemaValidatorV1.class);
  }

  String getInstanceIdentifier() {
    return confType.getInstanceIdentifier();
  }

  List<ConfigurationSource> getConfigurationSource() {
    return confType.getConfigurationAnchor().stream()
        .map(ConfigurationAnchorV1::new)
        .collect(Collectors.toList());
  }

  BigInteger getTimeStampingIntervalSeconds() {
    return confType.getTimeStampingIntervalSeconds();
  }

  ManagementServiceType getManagementService() {
    return confType.getManagementService();
  }
}

