import { createI18n } from 'vue-i18n';
import merge from 'deepmerge';
import messages from '@intlify/unplugin-vue-i18n/messages';

export default function createI18nForXrd(i18nMessages = {}) {
  return createI18n({
    locale: process.env.VUE_APP_I18N_LOCALE || 'en',
    fallbackLocale: process.env.VUE_APP_I18N_FALLBACK_LOCALE || 'en',
    silentFallbackWarn: true,
    allowComposition: true,
    messages: merge(messages, i18nMessages),
  });
}
