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
    File.read("../common-test/src/test/certs/admin-ca1.pem")
  end

  def read_admin_ca2_cert
    # Corresponding cert data: 
    #   Subject: '/CN=AdminCA2/O=EJBCA Sample/C=SE'
    #   Not before: 2012-09-06 13:44:00 UTC
    #   Not after: 2022-09-04 13:44:00 UTC
    File.read("../common-test/src/test/certs/admin-ca2.pem")
  end
end
