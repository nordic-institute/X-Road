const Events = require('events');

module.exports = class CustomClear2 extends Events {
  command(selector) {
    const { RIGHT_ARROW, BACK_SPACE } = this.api.Keys;
    return this.api.getValue(selector, (result) => {
      const chars = result.value.split('');
      chars.forEach(() => this.api.setValue(selector, RIGHT_ARROW));
      chars.forEach(() => this.api.setValue(selector, BACK_SPACE));
      this.emit('complete');
    });
  }
}


