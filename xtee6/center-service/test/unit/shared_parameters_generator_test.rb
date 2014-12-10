require 'test_helper'

java_import Java::ee.cyber.sdsb.common.conf.globalconf.SharedParametersSchemaValidator

class SharedParametersGeneratorTest < ActiveSupport::TestCase

  # Purpose of this test is to exercise all the logic of
  # SharedParametersGenerator and check it both via Schema validation as well
  # as visually. Database fixtures are chosen accordingly.
  test "Should generate shared parameters" do
    # Given
    generator = SharedParametersGenerator.new()

    # When
    xml = generator.generate()
    puts("Generated shared parameters XML:\n#{xml}")

    # Then
    SharedParametersSchemaValidator.validate(xml)
  end
end
