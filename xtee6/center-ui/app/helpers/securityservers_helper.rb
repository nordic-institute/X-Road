require 'base_helper'

module SecurityserversHelper
  include BaseHelper

  private

  def get_all_servers_as_json
    servers = SecurityServer.find(:all)
    servers_as_json = []

    servers.each do |server|
      servers_as_json << get_full_server_data_as_json(server)
    end

    servers_as_json
  end

  def get_full_server_data_as_json(server)
    {
      :server_code => server.server_code,
      :owner_name => server.owner.name,
      :owner_class => server.owner.member_class.code,
      :owner_code => server.owner.member_code,
      :registered => format_time(server.created_at.localtime),
      :address => server.address,
      :identifier => server.get_identifier
    }
  end
end