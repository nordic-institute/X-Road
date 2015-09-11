class DropTableDistributedSignedFiles < ActiveRecord::Migration
  def up
    drop_table :distributed_signed_files
  end

  def down
    create_table "distributed_signed_files", :force => true do |t|
      t.binary   "data"
      t.string   "data_boundary"
      t.binary   "signature"
      t.string   "sig_algo_id"
      t.datetime "created_at",    :null => false
      t.datetime "updated_at",    :null => false
    end
  end
end
