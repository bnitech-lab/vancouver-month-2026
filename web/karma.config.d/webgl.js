// The town's tests need WebGL. Headless Chrome has it only through SwiftShader, its software renderer,
// so the web tests run in a ChromeHeadless that is allowed to use it. The window has the same size on every
// machine, so a test sees the same page on a Mac and on Linux.
config.set({
  browsers: ['ChromeHeadlessWithWebGL'],
  customLaunchers: {
    ChromeHeadlessWithWebGL: {
      base: 'ChromeHeadless',
      flags: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--window-size=800,600'],
    },
  },
});
