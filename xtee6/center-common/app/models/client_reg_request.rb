# Request for adding client to a security server.
# Fields: security_server, sec_serv_user, origin
# Usage:
# ClientRegRequest.new(
#     :security_server => serverId,
#     :sec_serv_user => clientId,
#     :origin => origin).register()
class ClientRegRequest < RequestWithProcessing
  def self.find_by_server_and_client(server_id, client_id)
    Request.find_by_server_and_client(ClientRegRequest, server_id, client_id)
  end

  # Cancels client reg request with specific database id. Raises error if not
  # found.
  def self.cancel(request_id)
    reg_request = ClientRegRequest.find(request_id)

    raise "No client registration request with id '#{id}'" unless reg_request

    if !reg_request.can_cancel?
      raise "Cannot cancel client registration request "\
        "in state '#{reg_request.request_processing.status}' and with origin "\
        "'#{reg_request.origin}'"
    end

    del_request_server_id = reg_request.security_server.clean_copy()
    del_request_client_id = reg_request.sec_serv_user.clean_copy()

    comment = "#{request_id} deletion"

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
  end

  def get_canceling_request_id
    requests = ClientDeletionRequest.find_by_server_and_client(
        security_server, sec_serv_user)

    requests.first ? requests.first.id : nil
  end
end
