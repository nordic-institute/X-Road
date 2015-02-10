# Be sure to restart your server when you modify this file.

if defined?($servlet_context)
  CenterService::Application.config.session_store :java_servlet_store
else
  CenterService::Application.config.session_store :cache_store
end

# Use the database for sessions instead of the cookie-based default,
# which shouldn't be used to store highly confidential information
# (create the session table with "rails generate session_migration")
# CenterService::Application.config.session_store :active_record_store
