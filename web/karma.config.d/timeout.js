// The town's tests play days through the real change of day, and one plays a whole month, so a test may take
// longer than mocha's default two seconds.
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
// Karma ends the run when the browser says nothing for 30 seconds. Building the town on real Vancouver in
// SwiftShader can keep the browser busy that long on a slow GitHub runner without anything being wrong, so it
// waits as long as the longest test may take, twice over.
config.set({ browserNoActivityTimeout: 120000, captureTimeout: 120000 });
