$:.push File.expand_path("../lib", __FILE__)

# Maintain your gem's version:
require "center-common/version"

# Describe your gem and declare its dependencies:
Gem::Specification.new do |s|
  s.name        = "center-common"
  s.version     = CenterCommon::VERSION
  s.summary     = "Database and domain model layers of central server."
  s.author      = "Cybernetica AS"

  s.files = Dir["{app,config,lib}/**/*"] + ["Rakefile"]

  s.add_dependency "rails", "~> 3.2.10"
  s.add_dependency "transaction_isolation", "~> 1.0.3"
  s.add_dependency "foreigner", "~> 1.7.0"
end
