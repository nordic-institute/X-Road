ENV["RAILS_ENV"] = "test"
require File.expand_path('../../config/environment', __FILE__)
require 'rails/test_help'

class ActiveSupport::TestCase
  # Setup all fixtures in test/fixtures/*.(yml|csv) for all tests in alphabetical order.
  #
  # Note: You'll currently still have to declare fixtures explicitly in integration tests
  # -- they do not yet inherit this setting
  fixtures :all

  def get_riigiasutus
    id = ActiveRecord::Fixtures.identify(:riigiasutus)
    MemberClass.find(id)
  end

  def read_admin_ca1_cert
    # Corresponding cert data:
    #   Subject: '/CN=AdminCA1/O=EJBCA Sample/C=SE'
    #   Not before: 2012-06-14 10:04:29 UTC
    #   Not after: 2022-06-12 10:04:29 UTC
    return File.read("../common-test/src/test/certs/admin-ca1.pem")
  end

  def read_admin_ca2_cert
    # Corresponding cert data:
    #   Subject: '/CN=AdminCA2/O=EJBCA Sample/C=SE'
    #   Not before: 2012-09-06 13:44:00 UTC
    #   Not after: 2022-09-04 13:44:00 UTC
    return File.read("../common-test/src/test/certs/admin-ca2.pem")
  end

  def read_testorg_cert
    return File.read("../common-test/src/test/certs/testorg.pem")
  end

  # -- File upload helpers - start ---

  def assert_uploaded_file(file_name, content_type = nil)
    begin
      # Given
      file = File.new("test/resources/#{file_name}");
      uploaded_file = ActionDispatch::Http::UploadedFile.new(
          :tempfile => file,
          :filename => File.basename(file),
          :type => content_type
      )

      # When/then
      yield(uploaded_file)
    ensure
      file.close()
    end
  end

  def assert_upload_failure(file_name, content_type = nil)
    assert_uploaded_file(file_name, content_type) do |uploaded_file|
      assert_raises(RuntimeError) do
        yield(uploaded_file)
      end
    end
  end

  # -- File upload helpers - end ---
end
