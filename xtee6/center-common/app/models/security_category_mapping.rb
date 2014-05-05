class SecurityCategoryMapping < ActiveRecord::Base
  include Validators

  validates :security_category_id, :federated_sdsb_id, :present => true

  belongs_to :security_category
  belongs_to :federated_sdsb
end
