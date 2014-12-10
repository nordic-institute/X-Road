class ChangeTableNamesToConformWithBusinessModel < ActiveRecord::Migration
  def change
    rename_table :pkis, :approved_cas
    rename_table :approved_tsps, :approved_tsas
    rename_table :configuration_anchors, :trusted_anchors
  end
end
