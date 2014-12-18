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
end
