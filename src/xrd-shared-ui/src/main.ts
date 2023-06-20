/**
 * main.ts
 *
 * Bootstraps Vuetify and other plugins then mounts the App`
 */

// Components
import App from './App.vue'

// Composables
import { createApp } from 'vue'

// Plugins
import xrdSharedUi from './index'

createApp(App)
  .use(xrdSharedUi)
  .mount('#app')
