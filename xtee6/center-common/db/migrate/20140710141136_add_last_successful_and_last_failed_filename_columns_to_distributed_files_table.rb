class AddLastSuccessfulAndLastFailedFilenameColumnsToDistributedFilesTable < ActiveRecord::Migration
  def change
    add_column :distributed_files, :original_filename_last_successful, :string
    add_column :distributed_files, :original_filename_last_failed, :string
  end
end
