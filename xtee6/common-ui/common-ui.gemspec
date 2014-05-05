$:.push File.expand_path("../lib", __FILE__)

# Maintain your gem's version:
require "common-ui/version"

# Describe your gem and declare its dependencies:
Gem::Specification.new do |s|
  s.name        = "common-ui"
  s.version     = CommonUi::VERSION
  s.summary     = "TODO: Summary of CommonUi."
  s.description = "TODO: Description of CommonUi."

  s.files = Dir["{app,config,lib}/**/*"] + ["Rakefile"]

  s.add_dependency "rails", "~> 3.2.10"
end
