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

module MembersHelper
  private

  def get_all_member_classes
    MemberClass.find(:all)
  end

  def create_member_class_select(member_classes)
    select_content = []
    select_content << ""

    member_classes.each do |member_class|
      select_content << member_class.code
    end

    select_content
  end

  def member_classes
    MemberClass.get_all_codes
  end

  def xroad_instances
    instances = []

    ClientId.select("DISTINCT xroad_instance").each do |client_id|
      instances << client_id.xroad_instance
    end

    instances
  end

  def member_types
    types = []

    ClientId.select("DISTINCT object_type").each do |client_id|
      types << client_id.object_type
    end

    types
  end
end
