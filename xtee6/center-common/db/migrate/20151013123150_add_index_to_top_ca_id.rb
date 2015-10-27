class AddIndexToTopCaId < ActiveRecord::Migration
  def change
    add_index :approved_cas, :top_ca_id
  end
end
