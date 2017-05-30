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

java_import Java::java.security.cert.X509Certificate

java_import Java::ee.ria.xroad.common.util.CryptoUtils

# Methods added to this helper will be available to all templates in
# the application.

module ApplicationHelper

  include BaseHelper

  private

  def menu_items
    result = []

    configuration_submenu_items = get_configuration_submenu_items()
    if can_see_menu_item?(configuration_submenu_items)
      result << SubMenu.new(t('menu.configuration.title'),
          configuration_submenu_items)
    end

    management_submenu_items = get_management_submenu_items()
    if can_see_menu_item?(management_submenu_items)
      result << SubMenu.new(t('menu.management.title'),
          management_submenu_items)
    end

    result << SubMenu.new(t('menu.help.title'),
        [ MenuItem.new(t('menu.help.version'), :about) ])

    return result
  end

  def get_configuration_submenu_items
    return [
      MenuItem.new(t('menu.configuration.member'), :members, :view_members),
      MenuItem.new(t('menu.configuration.securityserver'), :securityservers,
          :view_security_servers),
      MenuItem.new(t('menu.configuration.group'), :groups,
          :view_global_groups),
      MenuItem.new(t('menu.configuration.central_service'),
          :central_services, :view_central_services),
      MenuItem.new(t('menu.configuration.approved_ca'),
          :approved_cas, :view_approved_cas),
      MenuItem.new(t('menu.configuration.tsp'), :tsps, :view_approved_tsas),
    ]
  end

  def get_management_submenu_items
    result = []

    result << MenuItem.new(t('menu.management.request'),
        :requests, :view_management_requests)

    result << MenuItem.new(t('menu.management.configuration_management'),
        :configuration_management, :view_configuration_management)

    result << MenuItem.new(t('menu.management.system_settings'),
        :system_settings, :view_system_settings)

    result << MenuItem.new(t('menu.management.import_v5'),
        :import, :execute_v5_import) if can_import_V5_data?

    result << MenuItem.new(t('menu.management.backup_and_restore'),
        :backup, :backup_configuration)

    result
  end

  def can_see_menu_item?(submenu_items)
    submenu_items.each do |each|
      return true if can?(each.privilege)
    end

    return false
  end

  def get_approved_ca_subject_names(approved_ca_top_CAs)
    subject_names = []
    approved_ca_top_CAs.each do |top_ca|
      cert = CryptoUtils.readCertificate(top_ca.cert)
      subject_names << cert.getSubjectDN.getName
    end
    subject_names.join("; ")
  end

  def can_import_V5_data?
    return File.exists?("/usr/share/xroad/bin/xtee55_clients_importer")
  end

  def url_and_cert_dialog(prefix)
    render(
      :partial => "shared/url_and_cert_dialog",
      :locals => { :prefix => prefix })
  end

end
