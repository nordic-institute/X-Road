class AddHistoryTableToSqliteSchema < ActiveRecord::Migration
  def change
    if ActiveRecord::Base.connection.adapter_name == "SQLite"
      # Create the history table for SQLite-based tests, too, because we
      # want to add HA support to all the relevant tables. Forking the
      # schema further would cause maintainability issues.
      create_table "history", :force => true do |t|
        t.integer  "id"
        t.string   "operation",  :null => false
        t.string   "table_name", :null => false
        t.integer  "record_id",  :null => false
        t.string   "field_name", :null => false
        t.text     "old_value"
        t.text     "new_value"
        t.string   "user_name",  :null => false
        t.datetime "timestamp",  :null => false
      end
    end
  end
end
