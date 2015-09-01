$CLASSPATH << "build/libs"

Dir.glob(File.expand_path("../../../build/libs/*.jar", __FILE__)).each do |file|
  require file
end

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

  # Use SQL instead of Active Record's schema dumper when creating the database.
  # This is necessary if your schema can't be completely dumped by the schema dumper,
  # like if you have constraints or database-specific column types.
  # XXX Make sure the version of pg_dump found first in your path matches the
  # version of Postgres used.
  # XXX Make sure the local connections are authenticated using the md5 scheme;
  # this is set in pg_hba.conf.
  config.active_record.schema_format = :sql
end
