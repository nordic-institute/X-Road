class CreateV5ImportTable < ActiveRecord::Migration
  def up
    create_table "v5_imports", :force => true do |t|
      t.string    "file_name"
      t.text      "console_output", :limit => nil
      t.datetime  "created_at", :null => false
    end
  end

  def down
    drop_table :v5_import
  end
end
