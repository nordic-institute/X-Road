/**
 * This is a hack to suppress the Vuetify Multiple instances of Vue detected warning.
 * See https://github.com/vuetifyjs/vuetify/issues/4068#issuecomment-446988490 for more information.
 */
export class SilenceWarnHack {

  /* tslint:disable */
  public originalLogError: any;

  constructor() {
    this.originalLogError = console.error;
  }
  public enable() {
    console.error = (...args: any) => {
      if (args[0].includes('[Vuetify]') && args[0].includes('https://github.com/vuetifyjs/vuetify/issues/4068')) { return; }
      this.originalLogError(...args);
    };
  }
  public disable() {
    console.error = this.originalLogError;
  }

  /* tslint:enable */
}
