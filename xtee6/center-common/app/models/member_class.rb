class MemberClass < ActiveRecord::Base
  include Validators

  validates :code, :unique => true
  has_many :member_class_mappings
  has_many :sdsb_members, :inverse_of => :member_class

  def self.find_by_code(code)
    MemberClass.where(:code => code).first
  end
end
