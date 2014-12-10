class ConfigurationSigningKey < ActiveRecord::Base
  belongs_to :configuration_source
end
