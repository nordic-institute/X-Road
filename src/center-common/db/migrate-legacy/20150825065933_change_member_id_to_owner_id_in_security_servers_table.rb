class ChangeMemberIdToOwnerIdInSecurityServersTable < ActiveRecord::Migration
  def up
    remove_foreign_key :security_servers, name: "security_servers_xroad_member_id_fk"

    rename_column :security_servers, :xroad_member_id, :owner_id

    add_foreign_key "security_servers", "security_server_clients", name: "security_servers_owner_id_fk", column: "owner_id", dependent: :delete
  end

  def down
    remove_foreign_key :security_servers, name: "security_servers_owner_id_fk"

    rename_column :security_servers, :owner_id, :xroad_member_id

    add_foreign_key "security_servers", "security_server_clients", name: "security_servers_xroad_member_id_fk", column: "xroad_member_id", dependent: :delete
  end
end
