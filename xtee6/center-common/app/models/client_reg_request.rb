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

# Request for adding client to a security server.
# Fields: security_server, sec_serv_user, origin
# Usage:
# ClientRegRequest.new(
#     :security_server => serverId,
#     :sec_serv_user => clientId,
#     :origin => origin).register()
class ClientRegRequest < RequestWithProcessing
  before_create do |rec|
    Request.set_server_owner_name(rec)
    Request.set_server_user_name(rec)
  end
  
  def self.find_by_server_and_client(server_id, client_id,
      processing_status = nil)
    Request.find_by_server_and_client(ClientRegRequest, server_id, client_id,
        processing_status)
  end

  # Revokes client reg request with specific database id. Raises error if not
  # found.
  def self.revoke(request_id)
    reg_request = ClientRegRequest.find(request_id)

    unless reg_request
      raise "No client registration request with id '#{request_id}'"
    end

    if !reg_request.can_revoke?
      raise "Cannot revoke client registration request "\
        "in state '#{reg_request.request_processing.status}' and with origin "\
        "'#{reg_request.origin}'"
    end

    del_request_server_id = reg_request.security_server.clean_copy()
    del_request_client_id = reg_request.sec_serv_user.clean_copy()

    comment = "Request ID #{request_id} revocation"

    ClientDeletionRequest.new(
         :security_server => del_request_server_id,
         :sec_serv_user => del_request_client_id,
         :comments => comment,
         :origin => reg_request.origin).register()
  end

  def find_processing
    ClientRegProcessing.find_by_server_and_client(
        security_server, sec_serv_user)
  end

  def new_processing
    ClientRegProcessing.new
  end

  def verify_request()
    if from_center?
      require_client(security_server.owner_id)
      require_security_server(security_server)
    end

    verify_against_submitted_requests()
    verify_against_existing_connections()
  end

  def verify_against_submitted_requests()
    processings = ClientRegRequest.find_by_server_and_client(
        security_server, sec_serv_user,
        RequestProcessing::SUBMITTED_FOR_APPROVAL)
    if processings != nil and not processings.empty?
      previous_request = processings.first

      raise InvalidClientRegRequestException.new(
          I18n.t("requests.duplicate_requests",
            :user => sec_serv_user,
            :security_server => security_server,
            :received => previous_request.created_at,
            :id => previous_request.id))
    end
  end

  def verify_against_existing_connections()
    server = SecurityServer.find_server_by_id(security_server)
    client = SecurityServerClient.find_by_id(sec_serv_user)

    if server != nil and client != nil and
        client.security_servers.include?(server)
      raise InvalidClientRegRequestException.new(
          I18n.t("requests.client_already_registered",
            :user => sec_serv_user,
            :security_server => security_server))
    end
  end

  def get_revoking_request_id
    requests = ClientDeletionRequest.find_by_server_and_client(
        security_server, sec_serv_user)

    requests.first ? requests.first.id : nil
  end
end
