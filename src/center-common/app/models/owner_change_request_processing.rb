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

# Processing info for changing the owner of a security server.
class OwnerChangeRequestProcessing < RequestProcessing
  def self.find_by_server_and_client(server_id, client_id)
    requests = OwnerChangeRequest.find_by_server_and_client(server_id, client_id)

    return processing_from_requests(requests)
  end

  def execute(request)
    server_id = request.security_server

    # Find the security server
    server = SecurityServer.find_server_by_id(server_id)
    if server == nil
      raise I18n.t("requests.server_not_found",
                  :server => request.security_server.to_s)
    end

    # Find the current owner.
    owner_id = request.security_server.owner_id
    owner = XRoadMember.find_by_id(owner_id)
    if owner == nil
      raise I18n.t("requests.client_not_found",
          :client => owner_id.to_s)
    end

    # Find the new owner.
    client = SecurityServerClient.find_by_id(request.sec_serv_user)
    if client == nil
      raise I18n.t("requests.client_not_found",
          :client => request.sec_serv_user.to_s)
    end

    # Remove new owner from security server clients
    client.security_servers.delete(server)

    # Update security server owner
    SecurityServer.update_owner(server_id, client)

    # Add current owner as security server client
    owner.security_servers << server

    # Update Security Server Owners global group
    owners_group = GlobalGroup.security_server_owners_group
    if owners_group != nil
      # New owner must be added as a member
      owners_group.add_member(request.sec_serv_user)
      # The current owner must be removed if this was its only server
      owners_group.remove_member(owner_id) if owner.owned_servers.size == 0
    end

  end
end
