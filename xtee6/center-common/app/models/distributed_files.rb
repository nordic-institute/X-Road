# This table will hold files to be distributed by the Central. It contains
# file name and file data (as blob) pairs.
class DistributedFiles < ActiveRecord::Base
  validates :file_name, :uniqueness => true

  def self.add_file(file_name, file_data, file_content_validator = nil)
    file_content_validator.validate(file_data) if file_content_validator

    DistributedFiles.where(file_name: file_name).destroy_all
    DistributedFiles.create!(:file_name => file_name, :file_data => file_data)
  end

  private
end
