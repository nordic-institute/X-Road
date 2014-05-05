java_import Java::ee.cyber.sdsb.common.conf.GlobalConf
java_import Java::ee.cyber.sdsb.common.identifier.SecurityServerId
java_import Java::ee.cyber.sdsb.common.request.ManagementRequestSender

module ManagementRequestHelper

  def register_client(client_id)
    request_sender.sendClientRegRequest(server_id, client_id)
  end

  def unregister_client(client_id)
    request_sender.sendClientDeletionRequest(server_id, client_id)
  end

  def register_cert(address, cert_bytes)
    request_sender.sendAuthCertRegRequest(server_id, address, cert_bytes)
  end

  def unregister_cert(address, cert_bytes)
    request_sender.sendAuthCertDeletionRequest(server_id, cert_bytes)
  end

  private

  def request_sender
    receiver = GlobalConf.getManagementRequestService
    sender = owner_identifier

    # TODO: what should userId be?
    ManagementRequestSender.new("userId", receiver, sender)
  end

  def server_id
    owner = owner_identifier
    server_code = serverconf.root.serverCode

    SecurityServerId.create(
      owner.sdsbInstance, owner.memberClass,
      owner.memberCode, server_code)
  end
end
