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

module CommonUi
  module UserUtils

    # assigned by an initializer in lib/common-ui/engine.rb
    @privilege_roles = {}

    class << self
      attr_accessor :privilege_roles

      def included(base)
        base.helper_method :current_user, :can?, :cannot?
      end
    end

    def servlet_request
      env['java.servlet_request']
    end

    def current_user
      return @current_user if @current_user

      user = User.new

      if servlet_request
        user.name = servlet_request.getRemoteUser

        UserUtils.privilege_roles.each do |privilege, roles|
          roles.each do |role|
            if servlet_request.isUserInRole(role)
              # logger.debug("adding #{role} privilege: #{privilege}")
              user.privileges << privilege.to_sym
            end
          end
        end
      elsif Rails.env.start_with?("devel") || Rails.env.test?
        user.name = "development"
        UserUtils.privilege_roles.each do |privilege, roles|
          user.privileges << privilege.to_sym
        end
      end

      @current_user = user
    end

    def authorize!(privilege)
      raise "not authorized" unless can?(privilege)
    end

    def can?(privilege)
      current_user.privileges.include?(privilege.to_sym)
    end

    def cannot?(privilege)
      !can?(privilege)
    end

    class User
      attr_accessor :name, :privileges

      def initialize
        @privileges = []
      end
    end
  end
end
