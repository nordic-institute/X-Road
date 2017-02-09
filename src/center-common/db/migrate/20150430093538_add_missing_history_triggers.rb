class AddMissingHistoryTriggers < ActiveRecord::Migration

  def up
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
DROP TRIGGER IF EXISTS update_history ON system_parameters;
CREATE TRIGGER update_history AFTER INSERT OR UPDATE OR DELETE ON system_parameters
    FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
      SQL
    end
  end

  def down
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
DROP TRIGGER IF EXISTS update_history ON system_parameters;
      SQL
    end
  end
end
