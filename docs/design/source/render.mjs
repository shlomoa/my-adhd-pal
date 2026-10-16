// Renders the boards of the design canvas to PNG (issue #164, aurora style) and writes render-manifest.json.
// Usage: node <path>/render.mjs <output dir>, run in a directory where playwright-core is installed; needs a Chromium (CHROMIUM_PATH, default /opt/pw-browsers/chromium).
// Phone boards and the overview: device scale 2 (390x844, P7 390x1000, overview 1200x640). Watch boards: the round face only,
// transparent outside the circle, 1254x1254 like the approved reference image of W4. W4 itself is that reference file and is
// not rendered; the manifest marks it as the reference.
import { createRequire } from 'module';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath, pathToFileURL } from 'url';

// playwright-core is resolved from the directory the script is run in (npm install playwright-core there).
const pw = await import(pathToFileURL(createRequire(path.join(process.cwd(), 'x')).resolve('playwright-core')));
const chromium = pw.chromium ?? pw.default.chromium;

const here = path.dirname(fileURLToPath(import.meta.url));
const out = process.argv[2];
if (!out) { console.error('usage: node render.mjs <output dir>'); process.exit(2); }
const boards = [
  ['P1', 'watch-tab', 'PhoneWatch', 390, 844], ['P2', 'tasks', 'PhoneTasks', 390, 844], ['P3', 'task-edit', 'PhoneTaskEdit', 390, 844],
  ['P4', 'reminder-edit', 'PhoneReminderEdit', 390, 844], ['P5', 'schedule', 'PhoneSchedule', 390, 844], ['P6', 'history', 'PhoneHistory', 390, 844],
  ['P7', 'settings', 'PhoneSettings', 390, 1000], ['P8', 'ask-your-watch', 'PhonePrompt', 390, 844], ['P9', 'allow-heart-rate', 'PhoneHeartRate', 390, 844],
  ['P10', 'alert-level-1', 'PhoneAlert1', 390, 844], ['P11', 'alert-level-2', 'PhoneAlert2', 390, 844], ['P12', 'loading-empty-error', 'PhoneStates', 390, 844],
  ['P13', 'five-link-states', 'PhoneLinkStates', 390, 844],
  ['W1', 'home', 'WatchHome'], ['W2', 'link-states', 'WatchLinks'], ['W3', 'reminder', 'WatchReminder'],
  ['W5', 'tile', 'WatchTile'], ['W6', 'tile-empty', 'WatchTileEmpty'],
  ['W7', 'alert-level-1', 'WatchAlert1'], ['W8', 'alert-level-2', 'WatchAlert2'], ['W9', 'allow-heart-rate', 'WatchPermission'],
  ['overview', '', 'Main', 1200, 640]];
const css = fs.readFileSync(path.join(here, 'ui.css'));
const manifest = { images: {}, reference: { W4: 'images/W4-choice.png' } };
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
for (const [id, name, file, w, h] of boards) {
  const boardBytes = fs.readFileSync(path.join(here, `${file}.dc.html`));
  manifest.images[id] = crypto.createHash('sha256').update(boardBytes).update(css).digest('hex');
  const markup = boardBytes.toString('utf8').match(/<x-dc>([\s\S]*?)<\/x-dc>/)[1].replace(/<helmet>[\s\S]*?<\/helmet>/, '');
  const html = `<!doctype html><meta charset=utf-8><style>${css}body{margin:0}</style><body>${markup}`;
  const watch = id.startsWith('W');
  const page = await browser.newPage({ viewport: { width: w || 340, height: h || 340 }, deviceScaleFactor: watch ? 1254 / 300 : 2 });
  await page.setContent(html, { waitUntil: 'load' });
  await page.waitForTimeout(100);
  const fname = id === 'overview' ? 'overview-screens.png' : `${id}-${name}.png`;
  if (watch) await page.locator('.w').screenshot({ path: path.join(out, fname), omitBackground: true });
  else await page.screenshot({ path: path.join(out, fname), clip: { x: 0, y: 0, width: w, height: h } });
  await page.close();
}
await browser.close();
fs.writeFileSync(path.join(here, 'render-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
