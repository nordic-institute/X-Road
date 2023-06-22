import type { App } from 'vue';
import merge from "deepmerge";

//resources&assets
import '@fontsource/open-sans';
import "@mdi/font/css/materialdesignicons.css";
import './assets/icons.css';
import messages from '@intlify/unplugin-vue-i18n/messages'

//plugins
import vuetify from "./plugins/vuetify";

import { createI18n } from "vue-i18n";
//components
import XrdComponent from "./components/XrdComponent.vue";
import XrdButton from "./components/XrdButton.vue";
import XrdCloseButton from "./components/XrdCloseButton.vue";
import XrdAlert from "./components/XrdAlert.vue";
import XrdEmptyPlaceholder from "./components/XrdEmptyPlaceholder.vue";
import XrdEmptyPlaceholderRow from "./components/XrdEmptyPlaceholderRow.vue";
import XrdExpandable from "./components/XrdExpandable.vue";
import XrdFileUpload from "./components/XrdFileUpload.vue";
import XrdFormLabel from "./components/XrdFormLabel.vue";
import XrdHelpIcon from "./components/XrdHelpIcon.vue";
import XrdSimpleDialog from "./components/XrdSimpleDialog.vue";
import XrdConfirmDialog from "./components/XrdConfirmDialog.vue";
import XrdHelpDialog from "./components/XrdHelpDialog.vue";
import XrdStatusIcon from "./components/XrdStatusIcon.vue";
import XrdSubViewContainer from "./components/XrdSubViewContainer.vue";
import XrdSubViewTitle from "./components/XrdSubViewTitle.vue";
import XrdSearch from "./components/XrdSearch.vue";
import XrdTable from "./components/XrdTable.vue";


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
      app.use(vuetify)
        .use(i18n)
        .component('XrdComponent', XrdComponent)
        .component('XrdButton', XrdButton)
        .component('XrdCloseButton', XrdCloseButton)
        .component('XrdAlert', XrdAlert)
        .component('XrdEmptyPlaceholder', XrdEmptyPlaceholder)
        .component('XrdEmptyPlaceholderRow', XrdEmptyPlaceholderRow)
        .component('XrdExpandable', XrdExpandable)
        .component('XrdFileUpload', XrdFileUpload)
        .component('XrdFormLabel', XrdFormLabel)
        .component('XrdHelpIcon', XrdHelpIcon)
        .component('XrdSimpleDialog', XrdSimpleDialog)
        .component('XrdConfirmDialog', XrdConfirmDialog)
        .component('XrdHelpDialog', XrdHelpDialog)
        .component('XrdStatusIcon', XrdStatusIcon)
        .component('XrdSubViewContainer', XrdSubViewContainer)
        .component('XrdSubViewTitle', XrdSubViewTitle)
        .component('XrdSearch', XrdSearch)
        .component('XrdTable', XrdTable)
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
  XrdEmptyPlaceholderRow,
  XrdExpandable,
  XrdFileUpload,
  XrdFormLabel,
  XrdHelpIcon,
  XrdSimpleDialog,
  XrdConfirmDialog,
  XrdHelpDialog,
  XrdStatusIcon,
  XrdSubViewContainer,
  XrdSubViewTitle,
  XrdSearch,
  XrdTable
};
