module.exports = {
  devServer: {
    proxy: 'http://xroad2-docker-ss6:8020' // this needs to be parametrized (address where backend runs)
  },
}
