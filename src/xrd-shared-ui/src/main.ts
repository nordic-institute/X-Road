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
import {createSharedUi} from './index'

createApp(App)
  .use(createSharedUi())
  .mount('#app')
