class FixHistoryColumnTypes < ActiveRecord::Migration
  def change
    adapter_name = ActiveRecord::Base.connection.adapter_name
    if adapter_name == "PostgreSQL"
      change_column :history, :old_value, :text
      change_column :history, :new_value, :text
    end
  end
end
