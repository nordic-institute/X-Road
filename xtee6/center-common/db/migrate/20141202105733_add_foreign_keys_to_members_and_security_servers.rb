class AddForeignKeysToMembersAndSecurityServers < ActiveRecord::Migration
  def up
    add_foreign_key(:security_servers, :security_server_clients,
        :column => 'sdsb_member_id', :dependent => :delete)
    add_foreign_key(:security_server_clients, :security_server_clients,
        :column => 'sdsb_member_id', :dependent => :delete)
    add_foreign_key(:security_server_clients, :member_classes,
        :dependent => :delete)
    add_foreign_key(:security_server_clients, :identifiers,
        :column => 'server_client_id', :dependent => :delete)
    add_foreign_key(:auth_certs, :security_servers, :dependent => :delete)
    add_foreign_key(:security_server_client_names, :identifiers,
        :column => 'client_identifier_id', :dependent => :delete)
    add_foreign_key(:security_servers_security_categories, :security_servers,
        :dependent => :delete)
    add_foreign_key(:security_servers_security_categories, :security_categories,
        :dependent => :delete)
    add_foreign_key(:server_clients, :security_servers,
        :dependent => :delete)
    add_foreign_key(:server_clients, :security_server_clients,
        :dependent => :delete)
  end

  def down
    remove_foreign_key(:security_servers, :column => 'sdsb_member_id')
    remove_foreign_key(:security_server_clients, :column => 'sdsb_member_id')
    remove_foreign_key(:security_server_clients, :member_classes)
    remove_foreign_key(:security_server_clients, :column => 'server_client_id')
    remove_foreign_key(:auth_certs, :security_servers)
    remove_foreign_key(:security_server_client_names, :column => 'client_identifier_id')
    remove_foreign_key(:security_servers_security_categories, :security_servers)
    remove_foreign_key(:security_servers_security_categories, :security_categories)
    remove_foreign_key(:server_clients, :security_servers)
    remove_foreign_key(:server_clients, :security_server_clients)
  end
end
