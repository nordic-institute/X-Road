#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

ENV["RAILS_ENV"] = "test"
require File.expand_path('../../config/environment', __FILE__)
require 'rails/test_help'

# Operations with signer software token are out of scope of controller tests.
class ApplicationController < BaseController

  private

  def software_token_initialized?
    return true
  end
end

class ActiveSupport::TestCase
  CN_CERT_CA1 = "/C=AAA/O=GOV/CN=AAA-central-external"
  # Setup all fixtures in test/fixtures/*.(yml|csv) for all tests in alphabetical order.
  #
  # Note: You'll currently still have to declare fixtures explicitly in integration tests
  # -- they do not yet inherit this setting
  fixtures :all

  def get_file_path(filename)
    "test/resources/#{filename}"
  end

  def get_file(filename)
    File.new(get_file_path(filename))
  end

  def get_riigiasutus
    id = ActiveRecord::Fixtures.identify(:riigiasutus)
    MemberClass.find(id)
  end

  # CN: '/C=AAA/O=GOV/CN=AAA-central-external'
  def read_cert_ca1
    return File.read("test/resources/cert_sign_external_AAA.pem")
  end

  def read_cert_ca2
    return File.read("test/resources/cert_sign_external_BBB.pem")
  end

  def read_testorg_cert
    return File.read("test/resources/cert_auth.pem")
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
