require 'fileutils'

class TrustedAnchor < ActiveRecord::Base
  has_many :anchor_urls, :autosave => true, :dependent => :destroy
  validates :instance_identifier, :uniqueness => true, :presence => true

  def self.add_anchor(anchor_unmarshaller, anchor_hash)
    instance_identifier = anchor_unmarshaller.get_instance_identifier

    anchor = TrustedAnchor.new(
        get_anchor_attributes(anchor_unmarshaller, anchor_hash))

    existing_anchor = TrustedAnchor.where(
        :instance_identifier => instance_identifier).first()

    if existing_anchor != nil
      existing_anchor.update_attributes!(
          get_anchor_attributes(anchor_unmarshaller, anchor_hash))
    else
      TrustedAnchor.create!(
          get_anchor_attributes(anchor_unmarshaller, anchor_hash))
    end
  end

  private

  def self.get_anchor_attributes(anchor_unmarshaller, anchor_hash)
    return {
        :instance_identifier => anchor_unmarshaller.get_instance_identifier,
        :trusted_anchor_file => anchor_unmarshaller.get_xml,
        :trusted_anchor_hash => anchor_hash,
        :generated_at => anchor_unmarshaller.get_generated_at,
        :anchor_urls => anchor_unmarshaller.get_anchor_urls
    }
  end
end
