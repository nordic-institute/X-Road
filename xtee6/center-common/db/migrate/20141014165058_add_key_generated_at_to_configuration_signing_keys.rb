class AddKeyGeneratedAtToConfigurationSigningKeys < ActiveRecord::Migration
  def change
    add_column :configuration_signing_keys, :key_generated_at, :timestamp
  end
end
