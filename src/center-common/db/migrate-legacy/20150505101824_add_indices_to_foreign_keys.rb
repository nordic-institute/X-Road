class AddIndicesToForeignKeys < ActiveRecord::Migration
  def change
    add_index :anchor_url_certs, :anchor_url_id
    add_index :anchor_urls,  :trusted_anchor_id
    add_index :auth_certs,  :security_server_id
    add_index :ca_infos, :top_ca_id
    add_index :ca_infos, :intermediate_ca_id
    add_index :central_services, :target_service_id
    add_index :configuration_signing_keys, :configuration_source_id
    add_index :configuration_sources, :active_key_id
    add_index :global_group_members, :global_group_id
    add_index :global_group_members, :group_member_id
    add_index :ocsp_infos, :ca_info_id
    add_index :requests, :security_server_id
    add_index :requests, :sec_serv_user_id
    add_index :requests, :request_processing_id
    add_index :security_server_client_names, :client_identifier_id
    add_index :security_server_clients, :xroad_member_id
    add_index :security_server_clients, :member_class_id
    add_index :security_server_clients, :server_client_id
    add_index :security_servers, :xroad_member_id
    # We cannot use default name here, as it will be too long (over 63 characters).
    add_index :security_servers_security_categories, :security_server_id, :name => "index_server_category_to_server_id"
    # We cannot use default name here, as it will be too long (over 63 characters).
    add_index :security_servers_security_categories, :security_category_id, :name => "index_server_to_category"
    add_index :server_clients, :security_server_id
    add_index :server_clients, :security_server_client_id
  end
end
