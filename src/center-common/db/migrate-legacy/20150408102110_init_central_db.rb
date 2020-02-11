# encoding: UTF-8
# Initial migration of the database of the Central server.

class InitCentralDb < ActiveRecord::Migration
  def up

  create_table "anchor_url_certs", :force => true do |t|
    t.integer "anchor_url_id"
    t.binary  "certificate"
  end

  create_table "anchor_urls", :force => true do |t|
    t.integer "trusted_anchor_id"
    t.string  "url"
  end

  create_table "approved_cas", :force => true do |t|
    t.string   "name"
    t.boolean  "authentication_only"
    t.string   "identifier_decoder_member_class"
    t.string   "identifier_decoder_method_name"
    t.datetime "created_at",                      :null => false
    t.datetime "updated_at",                      :null => false
  end

  create_table "approved_tsas", :force => true do |t|
    t.string   "name"
    t.string   "url"
    t.binary   "cert"
    t.datetime "valid_from"
    t.datetime "valid_to"
    t.datetime "created_at", :null => false
    t.datetime "updated_at", :null => false
  end

  create_table "auth_certs", :force => true do |t|
    t.integer  "security_server_id"
    t.binary   "certificate"
    t.datetime "created_at",         :null => false
    t.datetime "updated_at",         :null => false
  end

  create_table "ca_infos", :force => true do |t|
    t.binary   "cert"
    t.integer  "top_ca_id"
    t.integer  "intermediate_ca_id"
    t.datetime "valid_from"
    t.datetime "valid_to"
    t.datetime "created_at",         :null => false
    t.datetime "updated_at",         :null => false
  end

  create_table "central_services", :force => true do |t|
    t.string   "service_code"
    t.integer  "target_service_id"
    t.datetime "created_at",        :null => false
    t.datetime "updated_at",        :null => false
  end

  create_table "configuration_signing_keys", :force => true do |t|
    t.integer  "configuration_source_id"
    t.string   "key_identifier"
    t.binary   "certificate"
    t.datetime "key_generated_at"
    t.string   "token_identifier"
  end

  create_table "configuration_sources", :force => true do |t|
    t.string   "source_type"
    t.integer  "active_key_id"
    t.binary   "anchor_file"
    t.text     "anchor_file_hash"
    t.datetime "anchor_generated_at"
  end

  create_table "distributed_files", :force => true do |t|
    t.string   "file_name"
    t.binary   "file_data"
    t.string   "content_identifier"
    t.datetime "file_updated_at"
  end

  create_table "distributed_signed_files", :force => true do |t|
    t.binary   "data"
    t.string   "data_boundary"
    t.binary   "signature"
    t.string   "sig_algo_id"
    t.datetime "created_at",    :null => false
    t.datetime "updated_at",    :null => false
  end

  create_table "global_group_members", :force => true do |t|
    t.integer  "group_member_id"
    t.datetime "created_at",      :null => false
    t.datetime "updated_at",      :null => false
    t.integer  "global_group_id"
  end

  create_table "global_groups", :force => true do |t|
    t.string   "group_code"
    t.string   "description"
    t.integer  "member_count"
    t.datetime "created_at",   :null => false
    t.datetime "updated_at",   :null => false
  end

  create_table "identifiers", :force => true do |t|
    t.string   "object_type"
    t.string   "xroad_instance"
    t.string   "member_class"
    t.string   "member_code"
    t.string   "subsystem_code"
    t.string   "service_code"
    t.string   "server_code"
    t.string   "type"
    t.datetime "created_at",      :null => false
    t.datetime "updated_at",      :null => false
    t.string   "service_version"
  end

  create_table "member_classes", :force => true do |t|
    t.string   "code"
    t.string   "description"
    t.datetime "created_at",  :null => false
    t.datetime "updated_at",  :null => false
  end

  create_table "ocsp_infos", :force => true do |t|
    t.string   "url"
    t.binary   "cert"
    t.integer  "ca_info_id"
    t.datetime "created_at", :null => false
    t.datetime "updated_at", :null => false
  end

  create_table "request_processings", :force => true do |t|
    t.string   "type"
    t.string   "status"
    t.datetime "created_at", :null => false
    t.datetime "updated_at", :null => false
  end

  create_table "requests", :force => true do |t|
    t.integer  "request_processing_id"
    t.string   "type"
    t.integer  "security_server_id"
    t.integer  "sec_serv_user_id"
    t.binary   "auth_cert"
    t.string   "address"
    t.string   "origin"
    t.string   "server_owner_name"
    t.string   "server_user_name"
    t.text     "comments"
    t.datetime "created_at",            :null => false
    t.datetime "updated_at",            :null => false
    t.string   "server_owner_class"
    t.string   "server_owner_code"
    t.string   "server_code"
    t.string   "processing_status"
  end

  create_table "security_categories", :force => true do |t|
    t.string   "code"
    t.string   "description"
    t.datetime "created_at",  :null => false
    t.datetime "updated_at",  :null => false
  end

  create_table "security_server_client_names", :force => true do |t|
    t.string   "name"
    t.integer  "client_identifier_id"
    t.datetime "created_at",           :null => false
    t.datetime "updated_at",           :null => false
  end

  create_table "security_server_clients", :force => true do |t|
    t.string   "member_code"
    t.string   "subsystem_code"
    t.string   "name"
    t.integer  "xroad_member_id"
    t.integer  "member_class_id"
    t.integer  "server_client_id"
    t.string   "type"
    t.string   "administrative_contact"
    t.datetime "created_at",             :null => false
    t.datetime "updated_at",             :null => false
  end

  create_table "security_servers", :force => true do |t|
    t.string   "server_code"
    t.integer  "xroad_member_id"
    t.string   "address"
    t.datetime "created_at",     :null => false
    t.datetime "updated_at",     :null => false
  end

  create_table "security_servers_security_categories", :force => true do |t|
    t.integer "security_server_id",   :null => false
    t.integer "security_category_id", :null => false
  end

  create_table "server_clients", :force => true do |t|
    t.integer "security_server_id",        :null => false
    t.integer "security_server_client_id", :null => false
  end

  create_table "system_parameters", :force => true do |t|
    t.string   "key"
    t.string   "value"
    t.datetime "created_at", :null => false
    t.datetime "updated_at", :null => false
  end

  create_table "trusted_anchors", :force => true do |t|
    t.string   "instance_identifier"
    t.binary   "trusted_anchor_file"
    t.text     "trusted_anchor_hash"
    t.datetime "created_at"
    t.datetime "updated_at"
    t.datetime "generated_at"
  end

  create_table "ui_users", :force => true do |t|
    t.string   "username"
    t.string   "locale"
    t.datetime "created_at", :null => false
    t.datetime "updated_at", :null => false
  end

  create_table "v5_imports", :force => true do |t|
    t.string   "file_name"
    t.text     "console_output"
    t.datetime "created_at",     :null => false
  end

  add_foreign_key "anchor_url_certs", "anchor_urls", name: "anchor_url_certs_anchor_url_id_fk", dependent: :delete

  add_foreign_key "anchor_urls", "trusted_anchors", name: "anchor_urls_trusted_anchor_id_fk", dependent: :delete

  add_foreign_key "auth_certs", "security_servers", name: "auth_certs_security_server_id_fk", dependent: :delete

  add_foreign_key "ca_infos", "approved_cas", name: "ca_infos_intermediate_ca_id_fk", column: "intermediate_ca_id", dependent: :delete
  add_foreign_key "ca_infos", "approved_cas", name: "ca_infos_top_ca_id_fk", column: "top_ca_id", dependent: :delete

  add_foreign_key "central_services", "identifiers", name: "central_services_target_service_id_fk", column: "target_service_id", dependent: :nullify

  add_foreign_key "configuration_signing_keys", "configuration_sources", name: "configuration_signing_keys_configuration_source_id_fk", dependent: :delete

  add_foreign_key "configuration_sources", "configuration_signing_keys", name: "configuration_sources_active_key_id_fk", column: "active_key_id", dependent: :nullify

  add_foreign_key "global_group_members", "global_groups", name: "global_group_members_global_group_id_fk", dependent: :delete
  add_foreign_key "global_group_members", "identifiers", name: "global_group_members_group_member_id_fk", column: "group_member_id", dependent: :delete

  add_foreign_key "ocsp_infos", "ca_infos", name: "ocsp_infos_ca_info_id_fk", dependent: :delete

  add_foreign_key "requests", "identifiers", name: "requests_sec_serv_user_id_fk", column: "sec_serv_user_id", dependent: :delete
  add_foreign_key "requests", "identifiers", name: "requests_security_server_id_fk", column: "security_server_id", dependent: :delete
  add_foreign_key "requests", "request_processings", name: "requests_request_processing_id_fk", dependent: :delete

  add_foreign_key "security_server_client_names", "identifiers", name: "security_server_client_names_client_identifier_id_fk", column: "client_identifier_id", dependent: :delete

  add_foreign_key "security_server_clients", "identifiers", name: "security_server_clients_server_client_id_fk", column: "server_client_id", dependent: :delete
  add_foreign_key "security_server_clients", "member_classes", name: "security_server_clients_member_class_id_fk", dependent: :delete
  add_foreign_key "security_server_clients", "security_server_clients", name: "security_server_clients_xroad_member_id_fk", column: "xroad_member_id", dependent: :delete

  add_foreign_key "security_servers", "security_server_clients", name: "security_servers_xroad_member_id_fk", column: "xroad_member_id", dependent: :delete

  add_foreign_key "security_servers_security_categories", "security_categories", name: "security_servers_security_categories_security_category_id_fk", dependent: :delete
  add_foreign_key "security_servers_security_categories", "security_servers", name: "security_servers_security_categories_security_server_id_fk", dependent: :delete

  add_foreign_key "server_clients", "security_server_clients", name: "server_clients_security_server_client_id_fk", dependent: :delete
  add_foreign_key "server_clients", "security_servers", name: "server_clients_security_server_id_fk", dependent: :delete

end
end
