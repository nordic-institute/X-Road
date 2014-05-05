# encoding: UTF-8
# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# Note that this schema.rb definition is the authoritative source for your
# database schema. If you need to create the application database on another
# system, you should be using db:schema:load, not running all the migrations
# from scratch. The latter is a flawed and unsustainable approach (the more migrations
# you'll amass, the slower it'll run and the greater likelihood for issues).
#
# It's strongly recommended to check this file into your version control system.

ActiveRecord::Schema.define(:version => 20131202162342) do

  create_table "approved_tsps", :force => true do |t|
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

  create_table "distributed_files", :force => true do |t|
    t.string "file_name"
    t.text   "file_data"
  end

  create_table "distributed_signed_files", :force => true do |t|
    t.text     "data"
    t.string   "data_boundary"
    t.text     "signature"
    t.string   "sig_algo_id"
    t.datetime "created_at",    :null => false
    t.datetime "updated_at",    :null => false
  end

  create_table "federated_sdsbs", :force => true do |t|
    t.string   "code"
    t.string   "address"
    t.datetime "created_at", :null => false
    t.datetime "updated_at", :null => false
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
    t.string   "sdsb_instance"
    t.string   "member_class"
    t.string   "member_code"
    t.string   "subsystem_code"
    t.string   "service_code"
    t.string   "server_code"
    t.string   "type"
    t.datetime "created_at",     :null => false
    t.datetime "updated_at",     :null => false
  end

  create_table "member_class_mappings", :force => true do |t|
    t.string   "federated_member_class"
    t.integer  "member_class_id"
    t.integer  "federated_sdsb_id"
    t.datetime "created_at",             :null => false
    t.datetime "updated_at",             :null => false
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

  create_table "pkis", :force => true do |t|
    t.string   "name"
    t.boolean  "authentication_only"
    t.string   "name_extractor_member_class"
    t.string   "name_extractor_method_name"
    t.datetime "created_at",                  :null => false
    t.datetime "updated_at",                  :null => false
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
  end

  create_table "security_categories", :force => true do |t|
    t.string   "code"
    t.string   "description"
    t.datetime "created_at",  :null => false
    t.datetime "updated_at",  :null => false
  end

  create_table "security_category_mappings", :force => true do |t|
    t.integer  "security_category_id"
    t.integer  "federated_sdsb_id"
    t.string   "federated_category"
    t.datetime "created_at",           :null => false
    t.datetime "updated_at",           :null => false
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
    t.integer  "sdsb_member_id"
    t.integer  "member_class_id"
    t.integer  "server_client_id"
    t.string   "type"
    t.string   "administrative_contact"
    t.datetime "created_at",             :null => false
    t.datetime "updated_at",             :null => false
  end

  create_table "security_servers", :force => true do |t|
    t.string   "server_code"
    t.integer  "sdsb_member_id"
    t.string   "address"
    t.datetime "created_at",     :null => false
    t.datetime "updated_at",     :null => false
  end

  create_table "security_servers_security_categories", :id => false, :force => true do |t|
    t.integer "security_server_id",   :null => false
    t.integer "security_category_id", :null => false
  end

  create_table "server_clients", :id => false, :force => true do |t|
    t.integer "security_server_id",        :null => false
    t.integer "security_server_client_id", :null => false
  end

  create_table "system_parameters", :force => true do |t|
    t.string   "key"
    t.string   "value"
    t.datetime "created_at", :null => false
    t.datetime "updated_at", :null => false
  end

end
