class AddServiceVersionToIdentifiers < ActiveRecord::Migration
  def up
    add_column :identifiers, :service_version, :string
  end

  def down
    remove_column :identifiers, :service_version
  end
end
