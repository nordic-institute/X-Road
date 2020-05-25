#
# The MIT License
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
# OwnerChangeRequest.new(
#     :security_server => serverId,
#     :sec_serv_user => clientId,
#     :origin => origin).register()
class OwnerChangeRequest < RequestWithProcessing
  before_create do |rec|
    Request.set_server_owner_name(rec)
    Request.set_server_user_name(rec)
  end

  def self.find_by_server_and_client(server_id, client_id,
      processing_status = nil)
    Request.find_by_server_and_client(OwnerChangeRequest, server_id, client_id,
        processing_status)
  end

  # Revokes owner change request with specific database id. Raises error if not
  # found.
  def self.revoke(request_id)
    owner_change_request = OwnerChangeRequest.find(request_id)

    unless owner_change_request
      raise "No owner change request with id '#{request_id}'"
    end

    if !reg_request.can_revoke?
      raise "Cannot revoke owner change request "\
        "in state '#{owner_change_request.request_processing.status}' and with origin "\
        "'#{owner_change_request.origin}'"
    end

  end

  def find_processing
    OwnerChangeRequestProcessing.find_by_server_and_client(
        security_server, sec_serv_user)
  end

  def new_processing
    OwnerChangeRequestProcessing.new
  end

  def verify_request()
    if from_center?
      require_client(security_server.owner_id)
      require_security_server(security_server)
    end

    verify_against_submitted_requests()
    validate_request()
  end

  def verify_against_submitted_requests()
    processings = OwnerChangeRequest.find_by_server_and_client(
        security_server, sec_serv_user,
        RequestProcessing::SUBMITTED_FOR_APPROVAL)
    if processings != nil and not processings.empty?
      previous_request = processings.first

      raise InvalidOwnerChangeRequestException.new(
          I18n.t("requests.duplicate_owner_change_requests",
            :user => sec_serv_user,
            :security_server => security_server,
            :received => previous_request.created_at,
            :id => previous_request.id))
    end
  end

  def validate_request
    server = SecurityServer.find_server_by_id(security_server)
    client = SecurityServerClient.find_by_id(sec_serv_user)

    if server == nil
      raise InvalidOwnerChangeRequestException.new(
          I18n.t("requests.server_not_found",
            :server => security_server))
    end

    if client == nil
      raise InvalidOwnerChangeRequestException.new(
          I18n.t("requests.client_not_found",
            :client => sec_serv_user))
    end

    # Client cannot be a subsystem
    if client.subsystem_code != nil
      raise InvalidOwnerChangeRequestException.new(
          I18n.t("requests.must_be_member",
            :user => sec_serv_user))
    end

    # New owner must be registered as a client on the security server
    if !client.security_servers.include?(server)
      raise InvalidOwnerChangeRequestException.new(
          I18n.t("requests.not_registered_client",
            :user => sec_serv_user,
            :security_server => security_server))
    end

    # Client cannot be the current owner of the security server
    if server.get_server_id.matches_client_id(sec_serv_user)
      raise InvalidOwnerChangeRequestException.new(
          I18n.t("requests.already_owner",
            :user => sec_serv_user,
            :security_server => security_server))

    end

    # Check that server with the new server id does not exist yet
    existing_server = SecurityServer.find_server(
      server.server_code, client.member_code, client.member_class.code)

    if existing_server
      raise I18n.t("requests.server_code_exists",
        :member_class => client.member_class.code,
        :member_code => client.member_code,
        :server_code => server.server_code)
    end
  end

  def get_revoking_request_id
    nil
  end

end
