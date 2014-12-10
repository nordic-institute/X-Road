class DeleteTablesThatTurnedOutToBeUseless < ActiveRecord::Migration
  def up
    drop_table :federated_sdsbs
    drop_table :member_class_mappings
    drop_table :security_category_mappings
  end

  def down
    create_table "federated_sdsbs", :force => true do |t|
      t.string   "code"
      t.string   "address"
      t.datetime "created_at", :null => false
      t.datetime "updated_at", :null => false
    end

    create_table "member_class_mappings", :force => true do |t|
      t.string   "federated_member_class"
      t.integer  "member_class_id"
      t.integer  "federated_sdsb_id"
      t.datetime "created_at",             :null => false
      t.datetime "updated_at",             :null => false
    end

    create_table "security_category_mappings", :force => true do |t|
      t.integer  "security_category_id"
      t.integer  "federated_sdsb_id"
      t.string   "federated_category"
      t.datetime "created_at",           :null => false
      t.datetime "updated_at",           :null => false
    end
  end
end
