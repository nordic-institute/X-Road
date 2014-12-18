class UpdateRequestServerNames < ActiveRecord::Migration
  def up
    SdsbMember.find_each do |each|
      Request.update_names(
          each.member_class.code,
          each.member_code,
          each.name)
    end
  end

  def down
    # Do nothing, as there is no need for restoring inconsistent names
    # in th request.
  end
end
