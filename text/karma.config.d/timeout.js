// One test replays 400 months after every day of each, so on a slow machine it may take longer than mocha's
// default two seconds.
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
