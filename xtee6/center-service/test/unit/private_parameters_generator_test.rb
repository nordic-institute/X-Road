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

require 'test_helper'

java_import Java::ee.ria.xroad.common.conf.globalconf.PrivateParametersSchemaValidator

class PrivateParametersGenerator
  # We use cert in the source tree for testing.
  def get_central_server_ssl_cert_file
    return "test/resources/internal.crt"
  end
end

class PrivateParametersGeneratorTest < ActiveSupport::TestCase
  test "Should generate private parameters" do
    # Given
    instance_identifier = "EE"
    time_stamping_interval_seconds = 10

    generator = PrivateParametersGenerator.new()

    # When
    xml = generator.generate()
    puts("Generated private parameters XML:\n#{xml}")

    # Then
    PrivateParametersSchemaValidator.validate(xml)
  end
end
