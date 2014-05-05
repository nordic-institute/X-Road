# Request for registering authentication certificate of a security server.
# Fields: security_server, auth_cert, address, origin
# Origin: either :security_server or :center
# Usage:
# AuthCertRegRequest.new(
#     :security_server => serverId,
#     :auth_cert => base64bytes,
#     :address => "0.0.0.0",
#     :origin => origin).register()
class AuthCertRegRequest < RequestWithProcessing
  def find_processing
    AuthCertRegProcessing.find_by_server_and_cert(security_server, auth_cert)
  end

  def new_processing
    AuthCertRegProcessing.new
  end

  def verify_request()
    require_client(security_server.owner_id) if from_center?
  end

  # Cancels auth cert reg request with specific database id. Raises error if not
  # found.
  def self.cancel(request_id)
    reg_request = AuthCertRegRequest.find(request_id)

    raise "No auth cert registration request with id '#{id}'" unless reg_request

    if !reg_request.can_cancel?
      raise "Cannot cancel authentication certificate registration request "\
        "in state '#{reg_request.request_processing.status}' and with origin "\
        "'#{reg_request.origin}'"
    end

    del_request_server_id = reg_request.security_server.clean_copy()
    comment = "'#{request_id}' cancellation"

    AuthCertDeletionRequest.new(
         :security_server => del_request_server_id,
         :auth_cert => reg_request.auth_cert,
         :comments => comment,
         :origin => reg_request.origin).register()
  end

  def get_canceling_request_id
    request = AuthCertDeletionRequest.joins(:security_server).where(
      :identifiers => {
        :sdsb_instance => security_server.sdsb_instance,
        :member_class => security_server.member_class,
        :member_code => security_server.member_code,
        :server_code => security_server.server_code},
      :auth_cert => auth_cert,
      :origin => origin).first

    request ? request.id : nil
  end
end
