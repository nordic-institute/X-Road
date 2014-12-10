class CleanUpDistributedFilesTableAndAddUpdateAtColumn < ActiveRecord::Migration
  def up
    add_column :distributed_files, :file_updated_at, :datetime
    remove_column :distributed_files, :instance_identifier
    remove_column :distributed_files, :original_filename_last_successful
    remove_column :distributed_files, :original_filename_last_failed
    remove_column :distributed_files, :last_successful
    remove_column :distributed_files, :created_at
    remove_column :distributed_files, :updated_at
  end

  def down
    remove_column :distributed_files, :file_updated_at
    add_column :distributed_files, :instance_identifier, :string
    add_column :distributed_files, :original_filename_last_successful, :string
    add_column :distributed_files, :original_filename_last_failed, :string
    add_column :distributed_files, :last_successful, :boolean, default: true
    change_table :distributed_files do |t| t.timestamps end
  end
end
