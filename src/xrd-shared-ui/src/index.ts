import './assets/icons.css';
import type { App } from 'vue';

//plugins
import vuetify from "./plugins/vuetify";
import { createI18n } from "vue-i18n";
import messages from '@intlify/unplugin-vue-i18n/messages'

//components
import XrdComponent from "./components/XrdComponent.vue";
import XrdButton from "./components/XrdButton.vue";
import XrdCloseButton from "./components/XrdCloseButton.vue";
import XrdAlert from "./components/XrdAlert.vue";
import XrdEmptyPlaceholder from "./components/XrdEmptyPlaceholder.vue";
import XrdEmptyPlaceholderRow from "./components/XrdEmptyPlaceholderRow.vue";
import merge from "deepmerge";


function createSharedUi(i18nMessages = {}) {
  const i18n = createI18n({
    locale: 'en',
    fallbackLocale: 'en',
    silentFallbackWarn: true,
    allowComposition: true,
    messages: merge(messages, i18nMessages)
  })

  return {
    install(app: App) {
      console.log('Installing', messages, i18nMessages, )
      app.use(vuetify)
        .use(i18n)
        .component('XrdComponent', XrdComponent)
        .component('XrdButton', XrdButton)
        .component('XrdCloseButton', XrdCloseButton)
        .component('XrdAlert', XrdAlert)
        .component('XrdEmptyPlaceholder', XrdEmptyPlaceholder)
        .component('XrdEmptyPlaceholderRow', XrdEmptyPlaceholderRow)
      ;
    }
  }
}

export {
  createSharedUi,
  XrdComponent,
  XrdButton,
  XrdCloseButton,
  XrdAlert,
  XrdEmptyPlaceholder,
  XrdEmptyPlaceholderRow
};
