import './assets/icons.css';
import type { App } from 'vue';
import vuetify from "./plugins/vuetify";

import XrdComponent from "./components/XrdComponent.vue";
import XrdButton from "./components/XrdButton.vue";
import XrdCloseButton from "./components/XrdCloseButton.vue";
import XrdAlert from "./components/XrdAlert.vue";


function install(app: App) {
  app.use(vuetify)
    .component('XrdComponent', XrdComponent)
    .component('XrdButton', XrdButton)
    .component('XrdCloseButton', XrdCloseButton)
    .component('XrdAlert', XrdAlert)
  ;
}

export default { install };
export { XrdComponent, XrdButton, XrdCloseButton, XrdAlert };
