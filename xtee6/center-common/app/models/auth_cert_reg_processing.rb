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

# Processing info for registering authentication certificate of a
# security server.
class AuthCertRegProcessing < RequestProcessing
  def self.find_by_server_and_cert(server_id, cert)
    Rails.logger.info("find_by_server_and_cert(#{server_id})")

    requests = AuthCertRegRequest
        .joins(:security_server, :request_processing)
        .where(
          :identifiers => { # association security_server
            :xroad_instance => server_id.xroad_instance,
            :member_class => server_id.member_class,
            :member_code => server_id.member_code,
            :server_code => server_id.server_code},
          :request_processings => {:status => WAITING})

    # Filter by authentication cert.
    requests = requests.select { |req| req.auth_cert == cert}

    return processing_from_requests(requests)
  end

  # Executes the processing. The argument is the request received from
  # the security server (it should contain more information)
  def execute(request)
    server_id = request.security_server

    server = SecurityServer.find_server_by_id(server_id)

    if server == nil
      # We must create new security server object.
      Rails.logger.info("Creating new security server #{server}")

      # Find the owner.
      owner_id = request.security_server.owner_id
      owner = XroadMember.find_by_id(owner_id)
      if owner == nil
        raise I18n.t("requests.client_not_found",
            :client => owner_id.to_s)
      end

      server = SecurityServer.create!(
          # Use clean identifier object.
          :server_code => request.security_server.server_code,
          :owner => owner,
          :address => request.address)

      # Add owner to special group, if such group exists.
      owners_group = GlobalGroup.security_server_owners_group
      owners_group.add_member(server_id.owner_id) if owners_group != nil
    end

    # Server exists, we'll just have to add an auth cert
    Rails.logger.info("Adding auth cert to server #{server}")
    server.auth_certs.create!(:cert => request.auth_cert)
  end
end
