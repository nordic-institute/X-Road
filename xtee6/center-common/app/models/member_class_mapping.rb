class MemberClassMapping < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates_presence_of :member_class_id

  belongs_to :member_class
  belongs_to :federated_sdsb
end
