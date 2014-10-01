class UiUser < ActiveRecord::Base
  attr_accessible :locale, :username

  validates_with Validators::MaxlengthValidator
  validates :username, :uniqueness => true

  def self.find_by_username(username)
    UiUser.where(:username => username).first
  end
end
