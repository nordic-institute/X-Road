#
# The MIT License
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

class MemberClass < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :code, :uniqueness => true
  validates :description, :uniqueness => true

  has_many :member_class_mappings
  has_many :xroad_members, :inverse_of => :member_class,
      :class_name => "XRoadMember"

  before_destroy do |record|
    if record.xroad_members.any?
      raise I18n.t("errors.member_class.member_class_has_members", {
        :code => record.code.upcase
      })
    end
  end

  def self.find_by_code(code)
    result = MemberClass.where(:code => code).first

    unless result
      raise "No member class with code '#{code}' found"
    end

    result
  end

  def self.get_all_codes
    return MemberClass.all(:select => 'code').uniq.map(&:code)
  end

  def self.delete(code)
    find_by_code(code).destroy
  end
end
