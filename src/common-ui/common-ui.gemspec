$:.push File.expand_path("../lib", __FILE__)

# Maintain your gem's version:
require "common-ui/version"

# Describe your gem and declare its dependencies:
Gem::Specification.new do |s|
  s.name        = "common-ui"
  s.version     = CommonUi::VERSION
  s.summary     = "Common UI parts"
  s.author      = "Cybernetica AS"

  s.required_ruby_version = '~> 2.3.3'

  s.files = Dir["{app,config,lib}/**/*"] + ["Rakefile"]

  s.add_dependency "rails", "~> 3.2.0"
  s.add_dependency 'rack-cache', '~> 1.6.1'

  s.add_dependency "addressable", '~> 2.4.0'
  s.add_dependency "psych", '~> 3.0.3'
end
