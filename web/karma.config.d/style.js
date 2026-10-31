// The town's layout tests lay the page out as it is published: the page's own style sheet is handed to them, to put on
// the page for that test only. This snippet runs inside build/js/packages/<package>, four folders below the repository.
const pageStyle = require('path').resolve(__dirname, '..', '..', '..', '..', 'web', 'src', 'jsMain', 'resources', 'style.css');
config.client.pageStyle = require('fs').readFileSync(pageStyle, 'utf8');
