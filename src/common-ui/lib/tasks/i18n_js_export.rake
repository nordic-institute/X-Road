require "active_support/core_ext"

namespace :i18n do
  namespace :js do
    desc "Export translations to JS file(s)"

    TRANSLATIONS_FILE = "public/javascripts/translations.js"
    FILTER_PATTERN = "*"

    task :export do
      ::I18n.load_path = Dir[
        Rails.root.join('..', 'common-ui', 'config', 'locales', '*.{yml}').to_s]

      ::I18n.load_path << Dir[Rails.root.join('config', 'locales', '*.{yml}').to_s]

      File.open(TRANSLATIONS_FILE, "w+") do |f|
        f << %(I18n.translations || (I18n.translations = {});\n)
        filter(translations, FILTER_PATTERN).each do |locale, translations_for_locale|
          f << %(I18n.translations["#{locale}"] = #{translations_for_locale.to_json};\n);
        end
      end
    end

    def filter(translations, scopes)
      return translations if scopes.empty?

      scopes = scopes.split(".") if scopes.is_a?(String)
      scopes = scopes.clone
      scope = scopes.shift

      if scope == "*"
        results = {}

        translations.each do |scope, translations|
          if result = filter(translations, scopes)
            results[scope.to_sym] = result
          end
        end

        return results

      elsif translations.has_key?(scope.to_sym)
        return { scope.to_sym => filter(translations[scope.to_sym], scopes) }
      end

      nil
    end

    # Initialize and return all translations
    def translations
      ::I18n.backend.instance_eval do
        init_translations unless initialized?
        translations
      end
    end
  end
end
