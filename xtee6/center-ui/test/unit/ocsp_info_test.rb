require 'test_helper'

class OcspInfoTest < ActiveSupport::TestCase

  test "Should validate urls" do
    # Given
    urls = ["http://www.example.com", "invalidurl"]

    # When/then
    error = assert_raises(RuntimeError) do
      OcspInfo.validate_urls(urls)
    end

    assert(error.message.include?("invalidurl"))
  end
end
