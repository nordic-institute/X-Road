class SecurityCategory < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :code, :uniqueness => true

  has_and_belongs_to_many :security_servers,
      :join_table => "security_servers_security_categories"
  has_many :security_category_mappings

  before_destroy { |record| record.security_servers.clear }
end
