# Be sure to restart your server when you modify this file.

if defined?($servlet_context)
  CenterUi::Application.config.session_store :java_servlet_store
else
  CenterUi::Application.config.session_store :cache_store
end

# CenterUi::Application.config.session_store :cookie_store, key: '_ui_session'

# Use the database for sessions instead of the cookie-based default,
# which shouldn't be used to store highly confidential information
# (create the session table with "rails generate session_migration")
# Ui::Application.config.session_store :active_record_store
