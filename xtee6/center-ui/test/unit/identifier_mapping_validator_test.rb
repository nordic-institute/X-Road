require 'test_helper'

class IdentifierMappingValidatorTest < ActiveSupport::TestCase
  test "Should pass validation with valid mapping file" do
    assert_uploaded_file("identifiermapping.xml") do |file|
      IdentifierMappingValidator.new(file, "EE", ["GOV"]).validate()
    end
  end

  test "Should raise error when invalid XML" do
    assert_upload_failure("identifiermapping-INVALID.xml") do |file|
      IdentifierMappingValidator.new(file, "EE", ["GOV"]).validate()
    end
  end

  test "Should raise error when arbitrary content" do
    assert_upload_failure("import_wrong_extension.txt") do |file|
      IdentifierMappingValidator.new(file, "EE", ["GOV"]).validate()
    end
  end

  test "Should raise error when non-existent member classes" do
    assert_upload_failure("identifiermapping-MORE_MEMBER_CLASSES.xml") do |file|
      IdentifierMappingValidator.new(file, "EE", ["GOV"]).validate()
    end
  end

  test "Should raise error when wrong instance code" do
    assert_upload_failure("identifiermapping-WRONG_INSTANCE_CODE.xml") do |file|
      IdentifierMappingValidator.new(file, "EE", ["GOV"]).validate()
    end
  end
end
