# Methods added to this helper will be available to all templates in
# the application.

module ApplicationHelper

  include BaseHelper

  def menu_items
    [ SubMenu.new(t('menu.configuration.title'),
        [ MenuItem.new(t('menu.configuration.clients'), :clients, :view_clients),
          MenuItem.new(t('menu.configuration.sysparams'), :sysparams, :view_sys_params) ]),
      SubMenu.new(t('menu.management.title'),
        [ MenuItem.new(t('menu.management.async'), :async, :view_async_reqs),
          # MenuItem.new(t('menu.management.asynclog'), :asynclog, :view_async_reqs_log),
          MenuItem.new(t('menu.management.keys'), :keys, :view_keys),
          MenuItem.new(t('menu.management.backup_and_restore'), 
            :backup_and_restore,
            :backup_configuration)]), # TODO: Which privilege to use actually?
      SubMenu.new(t('menu.help.title'),
        [ MenuItem.new(t('menu.help.version'), :about) ])
    ]
  end

  def server_status_class
    return nil unless x55_installed?

    if !sdsb_activated?
      return "inactive"
    elsif sdsb_promoted?
      return "promoted"
    else
      return "dependent"
    end
  end
end
