class RenameColumnsForCertificate < ActiveRecord::Migration
  def up
    rename_column :anchor_url_certs, :certificate, :cert
    rename_column :auth_certs, :certificate, :cert

    # Removing index temporarily, as it causes undesirable side effects.
    remove_index :configuration_signing_keys, :configuration_source_id
    rename_column :configuration_signing_keys, :certificate, :cert
    add_index :configuration_signing_keys, :configuration_source_id
  end

  def down
    rename_column :anchor_url_certs, :cert, :certificate
    rename_column :auth_certs, :cert, :certificate

    # Removing index temporarily, as it causes undesirable side effects.
    remove_index :configuration_signing_keys, :configuration_source_id
    rename_column :configuration_signing_keys, :cert, :certificate
    add_index :configuration_signing_keys, :configuration_source_id
  end
end
