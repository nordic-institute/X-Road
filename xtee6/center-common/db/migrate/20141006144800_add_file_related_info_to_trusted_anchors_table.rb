class AddFileRelatedInfoToTrustedAnchorsTable < ActiveRecord::Migration
  def up
    # This one cannot be rolled back and I see no need to try it.
    change_column :configuration_sources, :anchor_file_hash, :text

    add_column :trusted_anchors, :trusted_anchor_file, :binary
    add_column :trusted_anchors, :trusted_anchor_hash, :text
    add_timestamps(:trusted_anchors)
  end

  def down
    remove_column :trusted_anchors, :trusted_anchor_file
    remove_column :trusted_anchors, :trusted_anchor_hash
    remove_timestamps(:trusted_anchors)
  end
end
