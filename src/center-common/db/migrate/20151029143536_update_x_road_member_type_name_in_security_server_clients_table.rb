class UpdateXRoadMemberTypeNameInSecurityServerClientsTable < ActiveRecord::Migration
  def up
    SecurityServerClient.where(:type => "XroadMember")\
        .update_all(:type => "XRoadMember")
  end

  def down
    SecurityServerClient.where(:type => "XRoadMember")\
        .update_all(:type => "XroadMember")
  end
end
