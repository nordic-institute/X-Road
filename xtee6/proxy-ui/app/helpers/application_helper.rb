# Methods added to this helper will be available to all templates in
# the application.

module ApplicationHelper

  include BaseHelper

  def menu_items
    configuration_submenu =
      SubMenu.new(t('menu.configuration.title'), [
        MenuItem.new(t('menu.configuration.clients'),
          :clients, :view_clients),
        MenuItem.new(t('menu.configuration.sysparams'),
          :sysparams, :view_sys_params)
      ])

    management_submenu =
      SubMenu.new(t('menu.management.title'), [
        MenuItem.new(t('menu.management.keys'),
          :keys, :view_keys),
        MenuItem.new(t('menu.management.backup_and_restore'),
          :backup, :backup_configuration)
      ])

    help_submenu =
      SubMenu.new(t('menu.help.title'), [
        MenuItem.new(t('menu.help.version'), :about)
      ])

    result = []

    if can_see_submenu?(configuration_submenu)
      result << configuration_submenu
    end

    if can_see_submenu?(management_submenu)
      result << management_submenu
    end

    if can_see_submenu?(help_submenu)
      result << help_submenu
    end

    result
  end

  def can_see_submenu?(submenu)
    submenu.subitems.each do |item|
      return true if !item.privilege || can?(item.privilege)
    end

    return false
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
