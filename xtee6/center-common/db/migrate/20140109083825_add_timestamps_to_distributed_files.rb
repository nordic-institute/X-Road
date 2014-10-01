class AddTimestampsToDistributedFiles < ActiveRecord::Migration
  def up
    change_table :distributed_files do |t| t.timestamps end
  end

  def down
    remove_column :distributed_files, :created_at
    remove_column :distributed_files, :updated_at
  end
end
