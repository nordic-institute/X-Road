class RestoreIdentifierDecoderData < ActiveRecord::Migration
  def self.up
    # restore approved_cas columns
    ApprovedCa.reset_column_information
    add_column(:approved_cas, :identifier_decoder_member_class, :string) unless ApprovedCa.column_names.include?('identifier_decoder_member_class')
    add_column(:approved_cas, :identifier_decoder_method_name, :string) unless ApprovedCa.column_names.include?('identifier_decoder_method_name')

    # add version column to distributed_files
    add_column :distributed_files, :version, :integer, :null => false, :default => 0

    # alter distributed_files constraints
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
DELETE FROM distributed_files where content_identifier='PRIVATE-PARAMETERS' OR content_identifier='SHARED-PARAMETERS';
ALTER TABLE distributed_files DROP CONSTRAINT unique_name;
ALTER TABLE distributed_files ADD CONSTRAINT unique_content_identifier_version_ha_node_name UNIQUE(content_identifier, version, ha_node_name);
      SQL
    end
  end

  def self.down
    # alter distributed_files constraints
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
DELETE FROM distributed_files where content_identifier='PRIVATE-PARAMETERS' OR content_identifier='SHARED-PARAMETERS';
ALTER TABLE distributed_files DROP CONSTRAINT unique_content_identifier_version_ha_node_name;
ALTER TABLE distributed_files ADD CONSTRAINT unique_name UNIQUE(file_name, ha_node_name);
      SQL
    end

    # drop version column from distributed_files
    remove_column(:distributed_files, :version)
  end
end
