# Use the test_pg environment, including the database configuration.
ENV["RAILS_ENV"] = "test_pg"

require File.expand_path('../../config/environment', __FILE__)
require 'rails/test_help'
