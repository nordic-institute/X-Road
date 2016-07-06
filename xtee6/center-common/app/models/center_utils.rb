class CenterUtils

  def self.format_time(time)
    time.to_i == 0 ? "&mdash;" : time.strftime(I18n.t('common.time_format'))
  end
end
