class SecurityCategoryMapping < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates_presence_of :security_category_id, :federated_sdsb_id

  belongs_to :security_category
  belongs_to :federated_sdsb
end
