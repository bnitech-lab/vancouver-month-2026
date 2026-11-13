// BalanceTest plays thousands of months, and the browser runs it too, so on a slow machine a test may take longer than
// mocha's default two seconds.
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
