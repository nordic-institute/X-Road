class AddGeneratedAtFieldToTrustedAnchors < ActiveRecord::Migration
  def change
    add_column :trusted_anchors, :generated_at, :timestamp
  end
end
