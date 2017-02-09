#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

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
