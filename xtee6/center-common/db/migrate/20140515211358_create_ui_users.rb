class CreateUiUsers < ActiveRecord::Migration
  def change
    create_table :ui_users, :force => true do |t|
      t.string :username
      t.string :locale

      t.timestamps
    end
  end
end
