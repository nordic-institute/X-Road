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

# Request for removing client from a security server.
# Fields: security_server, sec_serv_user, origin
# Usage:
# ClientDeletionRequest.new(
#     :security_server => serverId,
#     :sec_serv_user => clientId,
#     :comments => comments,
#     :origin => origin).register()
class ClientDeletionRequest < DeletionRequest
  before_create do |rec|
    Request.set_server_owner_name(rec)
    Request.set_server_user_name(rec)
  end

  def verify_request()
    require_security_server(security_server)
  end

  def execute()
    server = SecurityServer.find_server_by_id(security_server)
    client = SecurityServerClient.find_by_id(sec_serv_user)

    revoke_respective_reg_request()

    if client != nil && client.security_servers != nil
      client.security_servers.delete(server)
    end
  end

  def get_respective_reg_processing
    requests = Request.find_by_server_and_client(
        ClientRegRequest, security_server, sec_serv_user)

    return RequestProcessing.processing_from_requests(requests)
  end

  def self.find_by_server_and_client(server_id, client_id)
    logger.info("find_by_server_and_client(#{server_id}, #{client_id})")
    requests = ClientDeletionRequest.
        joins(:security_server, :sec_serv_user).
        where(
          :identifiers => { # association security_server
            :xroad_instance => server_id.xroad_instance,
            :member_class => server_id.member_class,
            :member_code => server_id.member_code,
            :server_code => server_id.server_code},
          :sec_serv_users_requests => { # association sec_serv_user
            :xroad_instance => client_id.xroad_instance,
            :member_class => client_id.member_class,
            :member_code => client_id.member_code,
            :subsystem_code => client_id.subsystem_code})

    # Filter for subsystem codes in sec_serv_user because this is cumbersome
    # to do with the SQL query.
    requests.select { |req|
        req.sec_serv_user.subsystem_code == client_id.subsystem_code
    }

    logger.debug("Requests returned: #{requests.inspect}")
    requests
  end
end
