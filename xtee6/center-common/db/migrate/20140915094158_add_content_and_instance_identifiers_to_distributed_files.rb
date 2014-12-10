class AddContentAndInstanceIdentifiersToDistributedFiles < ActiveRecord::Migration
  def change
    add_column :distributed_files, :instance_identifier, :string
    add_column :distributed_files, :content_identifier, :string
  end
end
