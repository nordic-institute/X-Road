class AddLastSuccessfulColumnToDistributedFiles < ActiveRecord::Migration
  def change
    add_column :distributed_files, :last_successful, :boolean, default: true
  end
end
