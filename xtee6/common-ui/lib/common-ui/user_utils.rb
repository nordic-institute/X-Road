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
