class MemberClass < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :code, :uniqueness => true
  validates :description, :uniqueness => true

  has_many :member_class_mappings
  has_many :xroad_members, :inverse_of => :member_class,
      :class_name => "XRoadMember"

  before_destroy do |record|
    if record.xroad_members.any?
      raise I18n.t("errors.member_class.member_class_has_members", {
        :code => record.code.upcase
      })
    end
  end

  def self.find_by_code(code)
    result = MemberClass.where(:code => code).first

    unless result
      raise "No member class with code '#{code}' found"
    end

    result
  end

  def self.get_all_codes
    return MemberClass.all(:select => 'code').uniq.map(&:code)
  end

  def self.delete(code)
    find_by_code(code).destroy
  end
end
