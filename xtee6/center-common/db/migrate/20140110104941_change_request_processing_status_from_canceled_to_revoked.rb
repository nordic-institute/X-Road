class ChangeRequestProcessingStatusFromCanceledToRevoked < ActiveRecord::Migration
  def up
    RequestProcessing.where(:status => "CANCELED").each do |each|
      each.update_attributes!(:status => "REVOKED")
    end
  end

  def down
    RequestProcessing.where(:status => "REVOKED").each do |each|
      each.update_attributes!(:status => "CANCELED")
    end
  end
end
