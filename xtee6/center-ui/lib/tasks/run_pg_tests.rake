# Rake task for running Postgres-specific tests.
namespace :test_pg do
  task :database do
    xroad_home = ENV["XROAD_HOME"]

    if !xroad_home || xroad_home.empty?
      raise "The environment variable XROAD_HOME must be set to project root dir!"
    end

    prod_db_tests_home = "#{xroad_home}/center-ui/test_pg/database"
    Dir.new(prod_db_tests_home).each do |file|
      if file.end_with?("_test.rb")
        test_file_path = "#{prod_db_tests_home}/#{file}"
        system("jruby -I test_pg #{test_file_path}")
        if $?.exitstatus != 0
          raise "Some Postgres-specific tests failed. See the log above."
        end
      end
    end
  end
end
