$CLASSPATH << "build/libs"
require "common-util-1.0.jar"
require "common-verifier-1.0.jar"
require "center-ui-1.0.jar"


# Needed in order to run CryptoUtils class properly in devel mode
require "bcpkix-jdk15on-1.47.jar"
require "bcprov-jdk15on-1.47.jar"

require "commons-lang3-3.1.jar"
require "commons-io-2.4.jar"
require "slf4j-api-1.6.6.jar"
require "apache-mime4j-core-0.7.2.jar"

CenterUi::Application.configure do
  # Settings specified here will take precedence over those in config/application.rb

  # In the development environment your application's code is reloaded on
  # every request. This slows down response time but is perfect for development
  # since you don't have to restart the web server when you make code changes.
  config.cache_classes = false

  # Log error messages when you accidentally call methods on nil.
  config.whiny_nils = true

  # Show full error reports and disable caching
  config.consider_all_requests_local       = true
  config.action_controller.perform_caching = false

  # Don't care if the mailer can't send
  # config.action_mailer.raise_delivery_errors = false

  # Print deprecation notices to the Rails logger
  config.active_support.deprecation = :log

  # Only use best-standards-support built into browsers
  config.action_dispatch.best_standards_support = :builtin
end
