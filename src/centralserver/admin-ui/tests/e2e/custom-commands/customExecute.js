/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
/**
 * A very basic Nightwatch custom command. The command name is the filename and the
 *  exported "command" function is the command.
 *
 * Example usage:
 *   browser.customExecute(function() {
 *     console.log('Hello from the browser window')
 *   });
 *
 * For more information on writing custom commands see:
 *   https://nightwatchjs.org/guide/extending-nightwatch/#writing-custom-commands
 *
 * @param {*} data
 */
exports.command = function command(data) {
  // Other Nightwatch commands are available via "this"

  // .execute() inject a snippet of JavaScript into the page for execution.
  //  the executed script is assumed to be synchronous.
  //
  // See https://nightwatchjs.org/api/execute.html for more info.
  //
  this.execute(
    // The function argument is converted to a string and sent to the browser
    function (argData) {
      return argData;
    },

    // The arguments for the function to be sent to the browser are specified in this array
    [data],

    function (result) {
      // The "result" object contains the result of what we have sent back from the browser window
      // eslint-disable-next-line no-console
      console.log('custom execute result:', result.value);
    },
  );

  return this;
};
