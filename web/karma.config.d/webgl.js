// The town's tests need WebGL. Headless Chrome has it only through SwiftShader, its software renderer,
// so the web tests run in a ChromeHeadless that is allowed to use it.
config.set({
  browsers: ['ChromeHeadlessWithWebGL'],
  customLaunchers: {
    ChromeHeadlessWithWebGL: {
      base: 'ChromeHeadless',
      flags: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
    },
  },
});
