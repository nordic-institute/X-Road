class RemoveDownloadUrlFromConfigurationSources < ActiveRecord::Migration
  def change
    remove_column :configuration_sources, :download_url
  end
end
