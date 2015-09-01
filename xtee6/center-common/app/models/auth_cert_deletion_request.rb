# Request for deleting the authentication certificate of a security server.
# Fields: security_server, auth_cert, origin
# Usage:
# AuthCertDeletionRequest.new(
#     :security_server => serverId,
#     :auth_cert => bytes,
#     :comments => comments,
#     :origin => origin).register()
class AuthCertDeletionRequest < DeletionRequest
  def verify_request()
    # Nothing to verify.
  end

  def execute()
    revoke_respective_reg_request()

    server = SecurityServer.find_server_by_id(security_server)
    if server != nil && server.auth_certs != nil
      for cert in Array.new(server.auth_certs)
        if cert.cert == auth_cert
          server.auth_certs.delete(cert)
          cert.destroy()
        end
      end
    end
  end

  def get_respective_reg_processing
    return AuthCertRegProcessing.find_by_server_and_cert(
        security_server,auth_cert)
  end
end
