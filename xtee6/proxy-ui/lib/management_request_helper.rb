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

java_import Java::ee.ria.xroad.common.conf.globalconf.GlobalConf
java_import Java::ee.ria.xroad.common.identifier.SecurityServerId
java_import Java::ee.ria.xroad.common.request.ManagementRequestSender

module ManagementRequestHelper

  private

  def register_client(client_id)
    request_sender.sendClientRegRequest(server_id, client_id)
  end

  def unregister_client(client_id)
    request_sender.sendClientDeletionRequest(server_id, client_id)
  end

  def register_cert(address, cert_bytes)
    request_sender.sendAuthCertRegRequest(server_id, address, cert_bytes)
  end

  def unregister_cert(cert_bytes)
    request_sender.sendAuthCertDeletionRequest(server_id, cert_bytes)
  end

  def request_sender
    GlobalConf.verifyValidity

    receiver = GlobalConf.getManagementRequestService
    sender = owner_identifier

    ManagementRequestSender.new("proxyUi", receiver, sender)
  end

  def server_id
    owner = owner_identifier
    server_code = serverconf.serverCode

    SecurityServerId.create(
      owner.xRoadInstance, owner.memberClass,
      owner.memberCode, server_code)
  end
end
