class AddForeignKeysToTheRestOfTheDatabase < ActiveRecord::Migration
  def up
    add_foreign_key(:configuration_signing_keys, :configuration_sources,
        :dependent => :delete)
    add_foreign_key(:configuration_sources, :configuration_signing_keys,
        :column => 'active_key_id', :dependent => :nullify)

    add_foreign_key(:ocsp_infos, :ca_infos,
        :dependent => :delete)
    add_foreign_key(:ca_infos, :approved_cas,
        :column => 'top_ca_id', :dependent => :delete)
    add_foreign_key(:ca_infos, :approved_cas,
        :column => 'intermediate_ca_id', :dependent => :delete)

    add_foreign_key(:global_group_members, :identifiers,
        :column => 'group_member_id', :dependent => :delete)
    add_foreign_key(:global_group_members, :global_groups,
        :dependent => :delete)

    add_foreign_key(:requests, :request_processings,
        :dependent => :delete)
    add_foreign_key(:requests, :identifiers,
        :column => 'security_server_id', :dependent => :delete)
    add_foreign_key(:requests, :identifiers,
        :column => 'sec_serv_user_id', :dependent => :delete)

    add_foreign_key(:anchor_url_certs, :anchor_urls,
        :dependent => :delete)
    add_foreign_key(:anchor_urls, :trusted_anchors,
        :dependent => :delete)

    add_foreign_key(:central_services, :identifiers,
        :column => 'target_service_id', :dependent => :nullify)
  end

  def down
    remove_foreign_key(:configuration_signing_keys, :configuration_sources)
    remove_foreign_key(:configuration_sources, :column => 'active_key_id')

    remove_foreign_key(:ocsp_infos, :ca_infos)
    remove_foreign_key(:ca_infos, :column => 'top_ca_id')
    remove_foreign_key(:ca_infos, :column => 'intermediate_ca_id')

    remove_foreign_key(:global_group_members, :column => 'group_member_id')
    remove_foreign_key(:global_group_members, :global_groups)

    remove_foreign_key(:requests, :request_processings)
    remove_foreign_key(:requests, :column => 'security_server_id')
    remove_foreign_key(:requests, :column => 'sec_serv_user_id')

    remove_foreign_key(:anchor_url_certs, :anchor_urls)
    remove_foreign_key(:anchor_urls, :trusted_anchors)

    remove_foreign_key(:central_services, :column => 'target_service_id')
  end
end
