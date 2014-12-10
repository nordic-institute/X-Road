class ChangeTableColumnsToConformNewModel < ActiveRecord::Migration
  # Two methods, because migration is not entirely automatically reversible.
  def up
    rename_column :approved_cas, :name_extractor_method_name, :identifier_decoder_method_name
    rename_column :approved_cas, :name_extractor_member_class, :identifier_decoder_member_class

    remove_column :trusted_anchors, :friendly_name

    rename_column :anchor_urls, :configuration_anchor_id, :trusted_anchor_id

    add_column :configuration_sources, :anchor_file, :binary
    add_column :configuration_sources, :anchor_file_hash, :string
    add_column :configuration_sources, :anchor_generated_at, :timestamp
  end

  def down
    rename_column :approved_cas, :identifier_decoder_method_name, :name_extractor_method_name
    rename_column :approved_cas, :identifier_decoder_member_class, :name_extractor_member_class

    add_column :trusted_anchors, :friendly_name, :string

    rename_column :anchor_urls, :trusted_anchor_id, :configuration_anchor_id

    remove_column :configuration_sources, :anchor_file
    remove_column :configuration_sources, :anchor_file_hash
    remove_column :configuration_sources, :anchor_generated_at
  end
end
