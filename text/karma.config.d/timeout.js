// The text tests run in the browser as well, and on a slow machine one may take longer than mocha's default two
// seconds.
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
