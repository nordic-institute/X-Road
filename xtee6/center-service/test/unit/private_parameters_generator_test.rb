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
