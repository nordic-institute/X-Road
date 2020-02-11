class MoveTopCaIdColumnIntoApprovedCasTable < ActiveRecord::Migration
  def up
    remove_foreign_key "ca_infos", name: "ca_infos_top_ca_id_fk"
    # Collect associations
    ca_info_assocs = []
    CaInfo.where("top_ca_id IS NOT NULL").find_each do |each|
      ca_info_assocs << { id: each.id, top_ca_id: each.top_ca_id }
    end


    remove_column :ca_infos, :top_ca_id

    add_column :approved_cas, :top_ca_id, :integer
    add_foreign_key "approved_cas", "ca_infos", name: "approved_cas_top_ca_id_fk", column: "top_ca_id", dependent: :delete

    # Restore associations
    ca_info_assocs.each do |each|
      ApprovedCa.find(each[:top_ca_id]).update_attributes!(top_ca_id: each[:id])
    end
  end

  def down
    approved_ca_assocs = []
    ApprovedCa.find_each do |each|
      approved_ca_assocs << { id: each.id, top_ca_id: each.top_ca_id }
    end

    remove_foreign_key "approved_cas", name: "approved_cas_top_ca_id_fk"
    remove_column :approved_cas, :top_ca_id

    add_column :ca_infos, :top_ca_id, :integer
    add_foreign_key "ca_infos", "approved_cas", name: "ca_infos_top_ca_id_fk", column: "top_ca_id", dependent: :delete

    # Restore associations
    approved_ca_assocs.each do |each|
      CaInfo.find(each[:top_ca_id]).update_attributes!(top_ca_id: each[:id])
    end
  end
end
