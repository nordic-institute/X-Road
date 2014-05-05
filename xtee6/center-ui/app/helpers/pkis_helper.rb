module PkisHelper
  private

  def get_pki_edit_privilege
    session[:editing_pki] == true ? :edit_approved_ca : :add_approved_ca
  end
end
