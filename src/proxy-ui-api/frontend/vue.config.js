module.exports = {
  devServer: {
    proxy: 'https://docker-rest-ui-ss.local:4000', // this needs to be parametrized (address where backend runs)
    https: true
  },
}
