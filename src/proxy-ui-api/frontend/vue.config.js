module.exports = {
  devServer: {
    proxy: 'http://localhost:8020' // this needs to be parametrized (address where backend runs)
  },
}
