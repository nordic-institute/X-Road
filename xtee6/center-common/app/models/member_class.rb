class MemberClass < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :code, :uniqueness => true
  validates :description, :uniqueness => true

  has_many :member_class_mappings
  has_many :sdsb_members, :inverse_of => :member_class

  def self.find_by_code(code)
    return MemberClass.where(:code => code).first
  end

  def self.get_all_codes
    return MemberClass.all(:select => 'code').uniq.map(&:code)
  end
end
