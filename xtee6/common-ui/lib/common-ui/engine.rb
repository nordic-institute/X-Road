require "common-ui/user_utils"

module CommonUi
  class Engine < ::Rails::Engine
    initializer "static assets" do |app|
      app.middleware.use ::ActionDispatch::Static, "#{root}/public"
    end

    initializer "load privileges" do |app|
      unless $rails_rake_task
        privileges_file = "#{app.root}/config/privileges.yml"

        if File.exists?(privileges_file)
          Rails.logger.debug("Reading #{privileges_file}")

          privileges = YAML.load_file(privileges_file)

          # convert array of hashes to a single hash of privilege -> roles
          UserUtils.privilege_roles = privileges.reduce({}) do |acc, privilege|
            acc[privilege.first[0]] = privilege.first[1]
            acc
          end
        else
          Rails.logger.warn("Could not find #{privileges_file}")
        end
      end
    end
  end
end
