class AnchorUrl < ActiveRecord::Base
  include Validators

  validates :url, :presence => true, :url => true

  has_many :anchor_url_certs, :autosave => true, :dependent => :destroy
end
