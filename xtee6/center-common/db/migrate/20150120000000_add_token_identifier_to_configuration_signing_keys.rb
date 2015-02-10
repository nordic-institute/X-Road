class AddTokenIdentifierToConfigurationSigningKeys < ActiveRecord::Migration
  def change
    add_column :configuration_signing_keys, :token_identifier, :string
  end
end
