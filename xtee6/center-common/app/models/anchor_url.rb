class AnchorUrl < ActiveRecord::Base
  has_many :anchor_url_certs, :autosave => true, :dependent => :destroy
end
