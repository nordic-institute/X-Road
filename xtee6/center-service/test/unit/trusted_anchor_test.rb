require 'test_helper'

# With the same API as AnchorUnmarshaller
# (using duck typing capabilities of Ruby)
class MockAnchorUnmarshaller
  def initialize(instance_identifier)
    @instance_identifier = instance_identifier
  end

  def get_instance_identifier
    return @instance_identifier
  end

  def get_generated_at
    return Time.strptime("2014-10-09T15:54:00", '%Y-%m-%dT%H:%M:%S')
  end

  def get_xml
    return "<MockXML>"
  end

  # Returns ActiveRecord AnchorUrl objects.
  def get_anchor_urls
    result = []

    result << AnchorUrl.new(
        :url => "http://anchorurl.example.com",
        :anchor_url_certs => [AnchorUrlCert.new(:cert =>"mockCert")])

    return result
  end
end

class TrustedAnchorTest < ActiveSupport::TestCase
  def assert_anchor(anchor, anchor_hash)
    assert_not_nil(anchor)
    assert_equal(anchor_hash, anchor.trusted_anchor_hash)
    assert_equal("<MockXML>", anchor.trusted_anchor_file)

    anchor_urls = anchor.anchor_urls
    assert_equal(1, anchor_urls.size)

    anchor_url = anchor_urls[0]
    assert_equal("http://anchorurl.example.com", anchor_url.url)

    assert_equal("mockCert", anchor_url.anchor_url_certs[0].cert)
  end

  test "Should add new anchor" do
    # Given
    anchor_unmarshaller = MockAnchorUnmarshaller.new("CCC")
    anchor_hash = "MockAnchorHash"

    # When
    TrustedAnchor.add_anchor(anchor_unmarshaller, anchor_hash)

    # Then
    assert_equal(2, TrustedAnchor.all.size())

    added_anchor = TrustedAnchor.where(:instance_identifier => "CCC").first()

    assert_anchor(added_anchor, anchor_hash)
  end

  test "Should update existing anchor" do
    # Given
    anchor_unmarshaller = MockAnchorUnmarshaller.new("LV")
    anchor_hash = "MockAnchorHash"

    # When
    TrustedAnchor.add_anchor(anchor_unmarshaller, anchor_hash)

    # Then
    assert_equal(1, TrustedAnchor.all.size())

    updated_anchor = TrustedAnchor.where(:instance_identifier => "LV").first()

    assert_anchor(updated_anchor, anchor_hash)
  end
end
