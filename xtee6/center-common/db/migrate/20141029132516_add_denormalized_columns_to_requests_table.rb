class AddDenormalizedColumnsToRequestsTable < ActiveRecord::Migration
  def up
    add_column :requests, :server_owner_class, :string
    add_column :requests, :server_owner_code, :string
    add_column :requests, :server_code, :string
    add_column :requests, :processing_status, :string

    # Denormalizing already existing entries
    Request.find_each do |each|
      each.processing_status = each.get_status()
      server = each.security_server

      if server != nil
        each.server_owner_class = server.member_class
        each.server_owner_code = server.member_code
        each.server_code = server.server_code
      end

      each.save!
    end
  end

  def down
    remove_column :requests, :server_owner_class
    remove_column :requests, :server_owner_code
    remove_column :requests, :server_code
    remove_column :requests, :processing_status
  end
end
