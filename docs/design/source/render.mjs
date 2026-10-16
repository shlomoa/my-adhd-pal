import { chromium } from 'playwright-core';
import fs from 'fs';
const [,, canvas, out] = process.argv;
const boards = [
 ['P1','watch-tab','PhoneWatch',390,844],['P2','tasks','PhoneTasks',390,844],['P3','task-edit','PhoneTaskEdit',390,844],
 ['P4','reminder-edit','PhoneReminderEdit',390,844],['P5','schedule','PhoneSchedule',390,844],['P6','history','PhoneHistory',390,844],
 ['P7','settings','PhoneSettings',390,1000],['P8','ask-your-watch','PhonePrompt',390,844],['P9','allow-heart-rate','PhoneHeartRate',390,844],
 ['P10','alert-level-1','PhoneAlert1',390,844],['P11','alert-level-2','PhoneAlert2',390,844],['P12','loading-empty-error','PhoneStates',390,844],
 ['P13','five-link-states','PhoneLinkStates',390,844],
 ['W1','home','WatchHome',340,340],['W2','link-states','WatchLinks',340,340],['W3','reminder','WatchReminder',340,340],
 ['W4','choice','WatchSelection',340,340],['W5','tile','WatchTile',340,340],['W6','tile-empty','WatchTileEmpty',340,340],
 ['W7','alert-level-1','WatchAlert1',340,340],['W8','alert-level-2','WatchAlert2',340,340],['W9','allow-heart-rate','WatchPermission',340,340],
 ['overview','','Main',1200,640]];
const css = fs.readFileSync(`${canvas}/project/ui.css`,'utf8');
const b = await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/opt/pw-browsers/chromium', args:['--no-sandbox']});
for (const [id,name,file,w,h] of boards) {
  const src = fs.readFileSync(`${canvas}/project/${file}.dc.html`,'utf8');
  const m = src.match(/<x-dc>([\s\S]*?)<\/x-dc>/); const body = m[1].replace(/<helmet>[\s\S]*?<\/helmet>/,'');
  const html = `<!doctype html><meta charset=utf-8><style>@font-face{font-family:x}${css}body{margin:0}</style><body>${body}`;
  const p = await b.newPage({viewport:{width:w,height:h},deviceScaleFactor:2});
  await p.setContent(html,{waitUntil:'load'}); await p.waitForTimeout(100);
  const fname = id==='overview' ? 'overview-screens.png' : `${id}-${name}.png`;
  await p.screenshot({path:`${out}/${fname}`,clip:{x:0,y:0,width:w,height:h}}); await p.close();
}
await b.close();
