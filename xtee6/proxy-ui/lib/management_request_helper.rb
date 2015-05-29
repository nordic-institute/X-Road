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
