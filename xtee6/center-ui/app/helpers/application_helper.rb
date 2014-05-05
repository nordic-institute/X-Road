java_import Java::java.security.cert.X509Certificate

java_import Java::ee.cyber.sdsb.common.util.CryptoUtils

# Methods added to this helper will be available to all templates in
# the application.

module ApplicationHelper

  include BaseHelper

  private

  def menu_items
    [ SubMenu.new(t('menu.configuration.title'),
        [
          
          MenuItem.new(t('menu.configuration.member'), :members, :view_members),
          MenuItem.new(t('menu.configuration.securityserver'), :securityservers,
              :view_security_servers),
          MenuItem.new(t('menu.configuration.group'), :groups,
              :view_global_groups),
          MenuItem.new(t('menu.configuration.central_service'),
              :central_services, :view_central_services),
          MenuItem.new(t('menu.configuration.pki'), :pkis, :view_approved_cas),
          MenuItem.new(t('menu.configuration.tsp'), :tsps, :view_approved_tsps),
        ]),
      SubMenu.new(t('menu.management.title'),
        [
          MenuItem.new(t('menu.management.request'), :requests,
              :view_management_requests),
          MenuItem.new(t('menu.management.import_v5'), :import,
              :execute_v5_import),
          MenuItem.new(t('menu.management.distributed_file'), :distributed_files,
              :view_distributed_files),
          MenuItem.new(t('menu.management.backup'), :backup,
              :backup_configuration),
          MenuItem.new(t('menu.management.restore'), :restore,
              :restore_configuration)
        ]),
      SubMenu.new(t('menu.help.title'),
        [ MenuItem.new(t('menu.help.version'), :about) ])
    ]
  end

  def get_pki_subject_names(pki_top_CAs)
    subject_names = []
    pki_top_CAs.each do |top_ca|
      cert = CryptoUtils.readCertificate(top_ca.cert)
      subject_names << cert.getSubjectDN.getName
    end
    subject_names.join("; ")
  end
end
