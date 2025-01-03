# Security Server Admin Service UI

UI application for Security Server Admin Service. 

Tools and libraries used in this project:
- [Vue.js](https://vuejs.org/)
- [Vuetify](https://vuetifyjs.com/)
- [Vue i18n](https://kazupon.github.io/vue-i18n/)
- [VeeValidate](https://vee-validate.logaretm.com/v4/)
- [Axios](https://axios-http.com/)
- [Pinia](https://pinia.esm.dev/)
- [Vite](https://vitejs.dev/)
- [pnpm](https://pnpm.io/)

## Building

Gradle with [Frontend Gradle plugin](https://siouan.github.io/frontend-gradle-plugin/) is used for building the project.
Frontend Gradle Plugin is also responsible for downloading correct version of Node.js and pnpm.

## Development

While `gradle` is used for production build, it is more convenient to use `pnpm` commands for development.

Requirements for invoking `pnpm` commands:
- [Node.js](https://nodejs.org/) (correct version can be found in root [gradle.properties](../../../gradle.properties))
- [pnpm](https://pnpm.io/)

### Install dependencies
```
pnpm install
```

### Run development server

Compiles and runs https server with hot-reload support for development. Server can be accessed at `https://localhost:8080`.

```
pnpm run dev
```

### Compiles and minifies for production
```
pnpm run build
```

### Run tests
```
pnpm run test
```

### Lints and fixes files
```
pnpm run lint
```

## Customize configuration

[Vite's Env variables](https://vitejs.dev/guide/env-and-mode#env-files) can be used to customize build environment.
To use custom Env variables, create `.env.local` file in the root of the project. 
Apart from variables in [.env](.env) file, `PROXY_ADDRESS` can be used to set target running admin service API. 
`PROXY_ADDRESS` default value is `https://localhost:4200`, which corresponds to `ss0` Admin Service API running in 
[docker compose environment](../../../../Docker/xrd-dev-stack/README.md).
