// The town's tests play days through the real change of day, and one plays a whole month, so a test may take
// longer than mocha's default two seconds.
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
