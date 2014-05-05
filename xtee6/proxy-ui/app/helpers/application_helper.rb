java_import Java::ee.cyber.sdsb.common.util.CryptoUtils

# Methods added to this helper will be available to all templates in
# the application.

module ApplicationHelper

  include BaseHelper

  def menu_items
    [ SubMenu.new(t('menu.configuration.title'),
        [ MenuItem.new(t('menu.configuration.clients'), :clients, :view_clients),
          MenuItem.new(t('menu.configuration.quicklist'), :quicklist, :view_subjects),
          MenuItem.new(t('menu.configuration.sysparams'), :sysparams, :view_sys_params) ]),
      SubMenu.new(t('menu.management.title'),
        [ MenuItem.new(t('menu.management.async'), :async, :view_async_reqs),
          # MenuItem.new(t('menu.management.asynclog'), :asynclog, :view_async_reqs_log),
          MenuItem.new(t('menu.management.keys'), :keys, :view_keys),
          MenuItem.new(t('menu.management.backup'), :backup,
              :backup_configuration),
          MenuItem.new(t('menu.management.restore'), :restore,
              :restore_configuration)]),
      SubMenu.new(t('menu.help.title'),
        [ MenuItem.new(t('menu.help.version'), :about) ])
    ]
  end
end
