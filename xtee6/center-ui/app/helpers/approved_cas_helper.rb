module ApprovedCasHelper
  private

  def get_approved_ca_edit_privilege
    session[:editing_approved_ca] == true ? :edit_approved_ca : :add_approved_ca
  end
end
