module MembersHelper
  private

  def get_all_member_classes
    MemberClass.find(:all)
  end

  def create_member_class_select(member_classes)
    select_content = []
    select_content << ""

    member_classes.each do |member_class|
      select_content << member_class.code
    end

    select_content
  end

  def member_classes
    MemberClass.get_all_codes
  end

  def sdsb_instances
    instances = []

    ClientId.select("DISTINCT sdsb_instance").each do |client_id|
      instances << client_id.sdsb_instance
    end

    instances
  end

  def member_types
    types = []

    ClientId.select("DISTINCT object_type").each do |client_id|
      types << client_id.object_type
    end

    types
  end
end
