class AddPrimaryKeyColumns < ActiveRecord::Migration

  def change
    add_column :server_clients, :id, :primary_key
    add_column :security_servers_security_categories, :id, :primary_key
  end

end
