test_data_file = './test/datagen/test_data.rb'

if File.exist?(test_data_file)
  require test_data_file
else
  puts "WARNING: Test data file not present, 'rake localtest:create_test_data'  \
  has no effect in this environment."
end



namespace :localtest do
  def cleanup_database
    puts "Starting database cleanup"

    Rake::Task['db:reset'].invoke()

    puts "Completed database cleanup"
  end

  def create_test_data
    puts "Starting creation of test data"

    ActiveRecord::Base.transaction do
      create_all_test_data()
    end

    puts "Completed creation of test data"
  end

  task :create_test_data => :environment do
    if File.exist?(test_data_file)
      cleanup_database()
      create_test_data()
    else
      puts "Test data not present, database remains unchanged."
    end
  end
end
