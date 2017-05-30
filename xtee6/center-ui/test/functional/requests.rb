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

# Functional test for creating and registering requests.

# Unique suffix to guarantee that this test will use unique names.
$unique_suffix = rand(1000000)

def client_id(member_code, subsystem_code = nil)
  ClientId.from_parts("EE", "ORG", "#{member_code}#{$unique_suffix}",
      subsystem_code)
end

def server_id(member_code, server_code)
  SecurityServerId.from_parts("EE", "ORG", "#{member_code}#{$unique_suffix}",
      server_code)
end

def cert_data(data)
  "#{data}#{$unique_suffix}"
end

def prepare_database()
  member_classes = MemberClass.where(:code => "ORG")
  if member_classes.length == 1
    member_class = member_classes[0]
  else
    member_class = MemberClass.create(
        :code => "ORG",
        :description => "Organization")
  end

  member_a = XroadMember.create(
      :member_class => member_class,
      :member_code => "MEM_A#{$unique_suffix}",
      :name => "Test member 1",
      :administrative_contact => "foo@bar.ee")
  member_b = XroadMember.create(
      :member_class => member_class,
      :member_code => "MEM_B#{$unique_suffix}",
      :name => "Test member 2",
      :administrative_contact => "foo@bar.ee")
  subsystem_b = Subsystem.create(
      :xroad_member => member_b,
      :subsystem_code => "SUBSYS_B")

  GlobalGroup.create(:group_code => "security-server-owners")
  SystemParameter.create(
      :key => SystemParameter::SECURITY_SERVER_OWNERS_GROUP,
      :value => "security-server-owners")
end

def cleanup_database()
  AuthCertRegRequest.delete_all()
  ClientRegRequest.delete_all()
  AuthCertDeletionRequest.delete_all()
  ClientDeletionRequest.delete_all()
  Subsystem.delete_all()
  XroadMember.delete_all()
  MemberClass.delete_all()
  SecurityServer.delete_all()
  SystemParameter.delete_all()
  GlobalGroup.delete_all()
end

def do_auth_cert_reg_request_simple()
  cert = cert_data("base64bytes")
  address = "1.2.3.4"

  first_request = AuthCertRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :auth_cert => cert,
      :address => address,
      :origin => Request::SECURITY_SERVER)

  first_request.register()

  second_request = AuthCertRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :auth_cert => cert,
      :address => address,
      :origin => Request::CENTER)

  second_request.register()

  # Register second cert too.
  cert = cert_data("secondCert")
  address = "4.3.2.1"

  first_request = AuthCertRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :auth_cert => cert,
      :address => address,
      :origin => Request::SECURITY_SERVER)

  first_request.register()

  second_request = AuthCertRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :auth_cert => cert,
      :address => address,
      :origin => Request::CENTER)

  second_request.register()
end

def do_client_reg_request_simple()
  # Add member to server
  first_request = ClientRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :sec_serv_user => client_id("MEM_B"),
      :origin => Request::SECURITY_SERVER)

  first_request.register()

  second_request = ClientRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :sec_serv_user => client_id("MEM_B"),
      :origin => Request::CENTER)

  second_request.register()

  # Add subsystem to server
  first_request = ClientRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :sec_serv_user => client_id("MEM_B", "SUBSYS_B"),
      :origin => Request::SECURITY_SERVER)

  first_request.register()

  second_request = ClientRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :sec_serv_user => client_id("MEM_B", "SUBSYS_B"),
      :origin => Request::CENTER)

  second_request.register()
end

def do_auth_cert_deletion_request_simple()
  # Delete non-existing cert
  request = AuthCertDeletionRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :auth_cert => cert_data("foobar"),
      :origin => Request::SECURITY_SERVER)

  request.register()

  # Delete existing
  request = AuthCertDeletionRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :auth_cert => cert_data("secondCert"),
      :origin => Request::SECURITY_SERVER)

  request.register()
end

def do_client_deletion_request_simple()
  # Delete non-existing client
  request = ClientDeletionRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :sec_serv_user => client_id("MEM_A"),
      :origin => Request::SECURITY_SERVER)

  request.register()

  # Delete existing
  request = ClientDeletionRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :sec_serv_user => client_id("MEM_B"),
      :origin => Request::SECURITY_SERVER)

  request.register()
end

def do_auth_cert_reg_request_unknown_member()
  # Request from security server MUST NOT check for members.
  request = AuthCertRegRequest.new(
      :security_server => server_id("MEM_X", "SERV1"),
      :auth_cert => cert_data("base64bytes"),
      :address => "1.2.3.4",
      :origin => Request::SECURITY_SERVER)
  request.register()

  # Request from center MUST check for members
  request = AuthCertRegRequest.new(
      :security_server => server_id("MEM_X", "SERV1"),
      :auth_cert => cert_data("base64bytes"),
      :address => "1.2.3.4",
      :origin => Request::CENTER)
  begin
    request.register()
  rescue
    # expected exception
  else
    raise "Should fail to find member"
  end
end

def do_auth_cert_reg_request_duplicates()
  request = AuthCertRegRequest.new(
      :security_server => server_id("MEM_A", "SERVX"),
      :auth_cert => cert_data("base64bytes"),
      :address => "1.2.3.4",
      :origin => Request::SECURITY_SERVER)
  request.register()
  begin
    request.register()
  rescue
    # expected exception
  else
    raise "Should fail to register duplicate"
  end
end

def do_auth_cert_deletion_request_no_server_or_client()
  # missing server
  request = AuthCertDeletionRequest.new(
      :security_server => server_id("MEM_A", "SERVX"),
      :auth_cert => cert_data("foobar"),
      :origin => Request::SECURITY_SERVER)
  begin
    request.register()
  rescue
    # expected exception
  else
    raise "Should fail to register"
  end

  # missing member
  request = AuthCertDeletionRequest.new(
      :security_server => server_id("MEM_X", "SERV1"),
      :auth_cert => cert_data("foobar"),
      :origin => Request::SECURITY_SERVER)
  begin
    request.register()
  rescue
    # expected exception
  else
    raise "Should fail to register"
  end
end

def do_client_deletion_request_no_server_or_client()
  request = ClientDeletionRequest.new(
      :security_server => server_id("MEM_X", "SERV1"),
      :sec_serv_user => client_id("MEM_A"),
      :origin => Request::SECURITY_SERVER)
  begin
    request.register()
  rescue
    # expected exception
  else
    raise "Should fail to register"
  end
end

def do_auth_cert_reg_request_no_origin()
  request = AuthCertRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :auth_cert => cert_data("base64bytes"),
      :address => "1.2.3.4",
      :origin => nil)
  begin
    request.register()
  rescue
    # expected exception
  else
    raise "Should fail to register without origin"
  end
end

def do_auth_cert_reg_request_wrong_origin()
  request = AuthCertRegRequest.new(
      :security_server => server_id("MEM_A", "SERV1"),
      :auth_cert => cert_data("base64bytes"),
      :address => "1.2.3.4",
      :origin => "foo")
  begin
    request.register()
  rescue
    # expected exception
  else
    raise "Should fail to register with wrong origin"
  end
end


# Clean up database
cleanup_database()

# Create test records in the database.
prepare_database()

# Run the actual test cases
do_auth_cert_reg_request_simple()
do_client_reg_request_simple()
do_auth_cert_deletion_request_simple()
do_client_deletion_request_simple()
do_auth_cert_reg_request_unknown_member()
do_auth_cert_reg_request_duplicates()
do_auth_cert_deletion_request_no_server_or_client()
do_client_deletion_request_no_server_or_client()
do_auth_cert_reg_request_no_origin()
do_auth_cert_reg_request_wrong_origin()

#cleanup_database()
