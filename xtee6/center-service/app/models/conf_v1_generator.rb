#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

java_import Java::ee.ria.xroad.common.conf.globalconf.PrivateParametersSchemaValidatorV1
java_import Java::ee.ria.xroad.common.conf.globalconf.SharedParametersSchemaValidatorV1

# Generates V1 configuration
class ConfV1Generator < ConfGenerator

  def getVersion
    return 1
  end

  def isCurrentVersion?
    return false
  end

  def generatePrivateParameters
    PrivateParametersGenerator.new().generate()
  end

  def validatePrivateParameters(private_parameters_xml)
    PrivateParametersSchemaValidatorV1.validate(private_parameters_xml)
  end

  def generateSharedParameters
    SharedParametersGenerator.new().generate()
  end

  def validateSharedParameters(shared_parameters_xml)
    SharedParametersSchemaValidatorV1.validate(shared_parameters_xml)
  end

end
