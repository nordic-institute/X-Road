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

# Processing info for adding client to a security server.
class ClientRegProcessing < RequestProcessing
  def self.find_by_server_and_client(server_id, client_id)
    requests = ClientRegRequest.find_by_server_and_client(server_id, client_id)

    return processing_from_requests(requests)
  end

  def execute(request)
    server_id = request.security_server

    server = SecurityServer.find_server_by_id(server_id)
    if server == nil
      raise I18n.t("requests.server_not_found",
                  :server => request.security_server.to_s)
    end

    client = SecurityServerClient.find_by_id(request.sec_serv_user)
    if client == nil
      raise I18n.t("requests.client_not_found",
          :client => request.sec_serv_user.to_s)
    end

    client.security_servers << server
  end
end
