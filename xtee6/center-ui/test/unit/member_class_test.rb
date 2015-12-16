require 'test_helper'

class MemberClassTest < ActiveSupport::TestCase

  test "Do not let delete member class with members" do
    # Given
    code = "riigiasutus"

    # When/then
    error = assert_raises(RuntimeError) do
      MemberClass.delete(code)
    end

    assert_equal(I18n.t(
        "errors.member_class.member_class_has_members", :code => code.upcase),
         error.message)
  end

  test "Delete empty member class" do
    MemberClass.delete("tyhiklass")
  end
end
