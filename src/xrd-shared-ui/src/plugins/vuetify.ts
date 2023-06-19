// Vuetify
import 'vuetify/styles'
import { createVuetify } from 'vuetify'

export default createVuetify({
  theme: {
    options: {
      customProperties: true,
    },
    themes: {
      light: {
        primary: '#663cdc',
        secondary: '#00C9E7',
        accent: '#8c9eff',
      },
    },
  },
});
