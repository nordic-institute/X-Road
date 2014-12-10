class CreateTablesForConfigurationAnchorsAndSources < ActiveRecord::Migration
  def change
    create_table "configuration_sources", :force => true do |t|
      t.string   "source_type"
      t.string   "download_url"
      t.integer  "active_key_id"
    end

    create_table "configuration_signing_keys", :force => true do |t|
      t.integer  "configuration_source_id"
      t.string   "key_identifier"
      t.binary   "certificate"
    end

    create_table "configuration_anchors", :force => true do |t|
      t.string   "friendly_name"
      t.string   "instance_identifier"
    end

    create_table "anchor_urls", :force => true do |t|
      t.integer  "configuration_anchor_id"
      t.string   "url"
    end

    create_table "anchor_url_certs", :force => true do |t|
      t.integer  "anchor_url_id"
      t.binary   "certificate"
    end
  end
end
