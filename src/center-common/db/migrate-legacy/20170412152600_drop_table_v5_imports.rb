class DropTableV5Imports < ActiveRecord::Migration
  def up
    # First drop the history related trigger on table v5_imports if we are using Postgres.
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
        DROP TRIGGER IF EXISTS update_history ON v5_imports;
      SQL
    end

    drop_table :v5_imports
  end

  def down
    create_table "v5_imports", :force => true do |t|
      t.string   "file_name"
      t.text     "console_output"
      t.datetime "created_at", :null => false
    end

    # Create triggers on table v5_imports if we are using Postgres.
    if ActiveRecord::Base.connection.adapter_name == "PostgreSQL"
      execute <<-SQL
        DROP TRIGGER IF EXISTS update_history ON v5_imports;
        CREATE TRIGGER update_history AFTER INSERT OR UPDATE OR DELETE ON v5_imports
          FOR EACH ROW EXECUTE PROCEDURE add_history_rows();
      SQL
    end
  end
end