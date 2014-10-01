class ChangeRequestProcessingStatusFromRejectedToDeclined < ActiveRecord::Migration
  def up
    RequestProcessing.where(:status => "REJECTED").each do |each|
      each.update_attributes!(:status => "DECLINED")
    end
  end

  def down
    RequestProcessing.where(:status => "DECLINED").each do |each|
      each.update_attributes!(:status => "REJECTED")
    end
  end
end
