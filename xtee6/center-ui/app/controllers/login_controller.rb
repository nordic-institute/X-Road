class LoginController < BaseLoginController

  skip_before_filter :check_conf
  skip_around_filter :wrap_in_transaction
  skip_before_filter :read_locale

end
