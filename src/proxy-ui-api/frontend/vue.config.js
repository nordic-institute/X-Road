module.exports = {
  devServer: {
    proxy: 'https://docker-rest-ui-ss.local:8443', // this needs to be parametrized (address where backend runs)
    https: true
  },
}
