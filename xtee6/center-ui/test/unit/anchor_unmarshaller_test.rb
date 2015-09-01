require 'test_helper'
require 'time'

class AnchorUnmarshallerTest < ActiveSupport::TestCase
  def get_anchor_file
    return "#{ENV["XROAD_HOME"]}/center-ui/test/resources/configuration-anchor-AAA.xml"
  end

  def get_expected_generated_at
    return Time.strptime("2014-10-09T15:54:00", '%Y-%m-%dT%H:%M:%S')
  end

  def assert_anchor_urls(actual_anchor_urls)
    assert_equal(1, actual_anchor_urls.size())

    anchor_url = actual_anchor_urls[0]
    assert_equal("http://iks2-fed0.cyber.ee/internalconf", anchor_url.url)

    certs = anchor_url.anchor_url_certs
    assert_equal(4, certs.size())

    assert_not_nil(certs[0].cert)
    assert_not_nil(certs[1].cert)
    assert_not_nil(certs[2].cert)
    assert_not_nil(certs[3].cert)
  end

  test "Should unmarshal anchor" do
    # Given/when
    unmarshaller = AnchorUnmarshaller.new(get_anchor_file())

    # Then
    assert_equal("AAA" , unmarshaller.get_instance_identifier())
    assert_equal(get_expected_generated_at() , unmarshaller.get_generated_at())
    assert_anchor_urls(unmarshaller.get_anchor_urls())
  end
end
