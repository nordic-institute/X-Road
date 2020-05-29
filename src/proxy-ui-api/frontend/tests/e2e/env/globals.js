var args = require('minimist')(process.argv);
var globalTestdata = '';

function getTestdata() {
  return args["testdata"] || "default";
}

module.exports = {  
  waitForConditionTimeout: 10000,
  get testdata() {
    if (!globalTestdata) {
      globalTestdata = getTestdata();
    }
    return globalTestdata;
  }
};
