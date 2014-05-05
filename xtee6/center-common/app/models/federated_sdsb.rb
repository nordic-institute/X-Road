class FederatedSdsb < ActiveRecord::Base
  include Validators

  validates :code, :unique => true

  has_many :member_class_mappings
  has_many :security_category_mappings
end
