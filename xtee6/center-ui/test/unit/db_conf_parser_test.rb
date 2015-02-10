require 'test_helper'

class DbConfParserTest < ActiveSupport::TestCase
  test "Should parse database conf from properties file" do
    # Given
    conf_parser = DbConfParser.new("production", "test/resources/db.properties")

    # When
    actual_conf = conf_parser.parse()

    # Then
    expected_conf = {
      "production"=> {
        "adapter"=>"postgresql",
        "encoding"=>"utf8",
        "username"=>"centerui",
        "password"=>"centerui",
        "database"=>"centerui_production",
        "reconnect"=>true
      }
    }

    assert_equal(expected_conf, actual_conf)
  end
end
