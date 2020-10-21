import Vue from 'vue';
import VueI18n from 'vue-i18n';
import en from 'vee-validate/dist/locale/en.json';

Vue.use(VueI18n);

import locals from './locales/en.json';
// Any is ok here, there is no definition for the locale format
// eslint-disable-next-line @typescript-eslint/no-explicit-any
(locals as any).validation = en.messages;

export default new VueI18n({
  locale: process.env.VUE_APP_I18N_LOCALE || 'en',
  fallbackLocale: process.env.VUE_APP_I18N_FALLBACK_LOCALE || 'en',
  silentFallbackWarn: true,
  messages: {
    en: locals,
  },
});
