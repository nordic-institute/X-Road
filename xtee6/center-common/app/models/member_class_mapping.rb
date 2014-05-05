class MemberClassMapping < ActiveRecord::Base
  include Validators

  validates :member_class_id, :present => true

  belongs_to :member_class
  belongs_to :federated_sdsb
end
