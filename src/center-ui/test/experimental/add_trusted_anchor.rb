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

require "watir-webdriver"

# Sometimes we need to wait a bit until JavaScript gets its job done.
DELAY_JS_SEC = 3

class SecurityOfficer
  def initialize(url, username, password)
    @url = url
    @username = username
    @password = password
    @browser = Watir::Browser.new(:firefox)
  end

  def login
    puts "-- Login: start ---"

    @browser.goto(@url)

    if can_skip_login?()
      @logged_in = true
      puts "-- Login: finished (actually not needed) ---"
      return
    end

    @browser.text_field(:id => "j_username").send_keys(@username)
    @browser.text_field(:id => "j_password").send_keys(@password)
    @browser.button(:class => "btn").click()

    unless @browser.div(:id => "server-info").exists?
      raise "Login failed, check if username and password are correct."
    end

    @logged_in = true

    puts "-- Login: finished successfully ---"
  end

  def upload_anchor(anchor_file, expected_description_elements)
    puts "-- Upload new anchor: start ---"

    verify_login()
    upload_anchor_execute(anchor_file, expected_description_elements)
    upload_anchor_verify(expected_description_elements)

    puts "-- Upload new anchor: finished successfully ---"
  end

  def finish
    @browser.close()
  end

  private

  def upload_anchor_execute(anchor_file, expected_description_elements)
    go_to_trusted_anchors()

    @browser.button(:id => "upload_trusted_anchor").click()

    @browser.file_field(
        :id => "upload_trusted_anchor_file").set(anchor_file)
    @browser.button(:id => "upload_trusted_anchor_ok").click()

    verify_presence_of_anchor_description(expected_description_elements)

    @browser.button(:id => "save_trusted_anchor_ok").click()
  end

  def upload_anchor_verify(expected_description_elements)
    puts "Verifying anchors in the database - start"

    go_to_trusted_anchors()

    wait_for_javascript_to_complete()

    trusted_anchors_content = @browser.div(:id => "trusted-anchors").text()

    verify_anchor_content(
        trusted_anchors_content, expected_description_elements)

    puts "Verifying anchors in the database - end"
  end

  def verify_presence_of_anchor_description(expected_description_elements)
    puts "Verifying presence of anchor description - start"

    wait_for_javascript_to_complete()

    actual_anchor_info = @browser.span(:id => "trusted_anchor_info").text()

    verify_anchor_content(actual_anchor_info, expected_description_elements)

    puts "Verifying presence of anchor description - end"
  end

  # -- Util methods - start ---

  def can_skip_login?
    return !@browser.url.end_with?("/login")
  end

  def go_to_trusted_anchors
    @browser.goto("#@url/configuration_management")
    @browser.link(:text => "Trusted Anchors").click()
  end

  def verify_login
    unless @logged_in
      raise "User must be logged in to perform aforementioned action!"
    end
  end

  def verify_anchor_content(content, expected_description_elements)
    expected_instance_id = expected_description_elements[:instance_identifier]
    expected_generated_at = expected_description_elements[:generated_at]
    expected_hash = expected_description_elements[:hash]

    unless content.include?(expected_instance_id)
      raise "Content does not include instance identifier "\
          "'#{expected_instance_id}', but should."
    end

    unless content.include?(expected_generated_at)
      raise "Content does not include generation time "\
          "'#{expected_generated_at}', but should."
    end

    unless content.include?(expected_hash)
      raise "Content does not include hash "\
          "'#{expected_hash}', but should."
    end
  end

  def wait_for_javascript_to_complete
    sleep DELAY_JS_SEC # Waiting for JS.
  end
  # -- Util methods - end ---
end

def verify_preconditions
  if ARGV.size < 1
    raise "At least server URL must be provided as argument.\n"\
        "Optional arguments are server username and password."
  end

  xroad_home = ENV["XROAD_HOME"]
  if !xroad_home || xroad_home.empty?
    raise "Environment variable 'XROAD_HOME' must be set!"
  end
end

def get_first_anchor_file
  return "#{ENV["XROAD_HOME"]}/center-ui/test/resources/configuration-anchor-AAA.xml"
end

def get_first_anchor_expectations
  return {
    :instance_identifier => "AAA",
    :generated_at => "2014-10-09 15:54:00",
    :hash => "mqZReMNW7Dv0jft6pvY6iy8lUhnHwkbP75HMWA=="
  }
end

# Let the action begin...
verify_preconditions()

url = ARGV[0]
username = ARGV[1] || "xroadui"
password = ARGV[2] || "Vaarikas456"

security_officer = SecurityOfficer.new(url, username, password)
security_officer.login()
security_officer.upload_anchor(
    get_first_anchor_file(), get_first_anchor_expectations())
security_officer.finish()
