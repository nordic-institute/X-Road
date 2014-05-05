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
