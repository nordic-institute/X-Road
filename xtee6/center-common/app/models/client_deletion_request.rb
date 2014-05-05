# Request for removing client from a security server.
# Fields: security_server, sec_serv_user, origin
# Usage:
# ClientDeletionRequest.new(
#     :security_server => serverId,
#     :sec_serv_user => clientId,
#     :comments => comments,
#     :origin => origin).register()
class ClientDeletionRequest < DeletionRequest
  def verify_request()
    require_security_server(security_server)
    # TODO: in some cases the client does not exist.
    # In these cases we should look for previous client addition request
    # require_client(sec_serv_user)
  end

  def execute()
    server = SecurityServer.find_server_by_id(security_server)
    client = SecurityServerClient.find_by_id(sec_serv_user)

    cancel_respective_reg_request()

    if client != nil && client.security_servers != nil
      client.security_servers.delete(server)
    end
  end

  def get_respective_reg_processing
    requests = Request.find_by_server_and_client(
        ClientRegRequest, security_server, sec_serv_user)

    return RequestProcessing.processing_from_requests(requests)
  end

  def self.find_by_server_and_client(server_id, client_id)
    logger.info("find_by_server_and_client(#{server_id}, #{client_id})")
    requests = ClientDeletionRequest
        .joins(:security_server, :sec_serv_user)
        .where(
          :identifiers => { # association security_server
            :sdsb_instance => server_id.sdsb_instance,
            :member_class => server_id.member_class,
            :member_code => server_id.member_code,
            :server_code => server_id.server_code},
          :sec_serv_users_requests => { # association sec_serv_user
            :sdsb_instance => client_id.sdsb_instance,
            :member_class => client_id.member_class,
            :member_code => client_id.member_code,
            :subsystem_code => client_id.subsystem_code})

    # Filter for subsystem codes in sec_serv_user because this is cumbersome
    # to do with the SQL query.
    requests.select { |req|
        req.sec_serv_user.subsystem_code == client_id.subsystem_code
    }

    logger.debug("Requests returned: #{requests.inspect}")
    requests
  end
end
