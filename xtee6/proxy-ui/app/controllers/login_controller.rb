class LoginController < BaseLoginController

  skip_around_filter :transaction
  skip_before_filter :check_conf, :read_server_id, :read_owner_name, :read_locale

  # not being able to read server id is ok here
  before_filter :only => :index do
    begin
      transaction do
        read_server_id
      end
    rescue
      error($!.message)
    end
  end
end
