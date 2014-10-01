class DistributedSignedFiles < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
end