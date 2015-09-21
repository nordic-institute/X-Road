class ServerClient < ActiveRecord::Base
  belongs_to :security_server
  belongs_to :security_server_client
end

