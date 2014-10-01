class FederatedSdsb < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :code, :uniqueness => true

  has_many :member_class_mappings
  has_many :security_category_mappings
end
