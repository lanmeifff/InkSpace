// 临时预览：把 logo.svg 的图形内联进 HTML，再用无头 Chrome 截图看真实字体效果
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const logo = fs.readFileSync(path.join(__dirname, '..', 'frontend', 'public', 'logo.svg'), 'utf8');
const group = logo.match(/<g fill="#1c1917">([\s\S]*?)<\/g>/)[1];
const shapes = group.replace(/<text[\s\S]*?<\/text>/, '');
const text = group.match(/<text[\s\S]*?<\/text>/)[0];

const html = `<!doctype html>
<html lang="zh"><head><meta charset="utf-8"><title>BrandMark preview</title>
<style>
  body { background:#faf8f5; color:#1c1917; margin:0; padding:26px 30px;
         font-family: ui-sans-serif,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif; }
  .row { display:flex; align-items:flex-end; gap:26px; margin-bottom:20px; }
  .cell { text-align:center; font-size:11px; color:#78716c; }
  .ink { color:#1c1917; } .soft { color:#44403c; } .brand { color:#c2410c; }
  svg { display:block; }
</style></head><body>
<div class="row">
${[
  ['180px', 180, 'ink', true],
  ['96px', 96, 'ink', true],
  ['56px', 56, 'soft', true],
  ['44px 侧栏', 44, 'ink', true],
  ['36px', 36, 'ink', true],
]
  .map(
    ([label, size, cls, withText]) => `  <div class="cell"><svg viewBox="0 0 768 768" width="${size}" height="${size}" class="${cls}">${shapes}${withText ? text : ''}</svg><div>${label}</div></div>`,
  )
  .join('\n')}
</div>
<div class="row">
${[
  ['180px 图形', 180, 'ink', false],
  ['96px 图形', 96, 'ink', false],
  ['56px 图形', 56, 'soft', false],
  ['36px 图形', 36, 'ink', false],
  ['24px 图形', 24, 'ink', false],
  ['180px 主色', 180, 'brand', true],
]
  .map(
    ([label, size, cls, withText]) => `  <div class="cell"><svg viewBox="0 0 768 768" width="${size}" height="${size}" class="${cls}">${shapes}${withText ? text : ''}</svg><div>${label}</div></div>`,
  )
  .join('\n')}
</div>
</body></html>`;

const htmlPath = path.join(__dirname, 'brandmark-preview.html');
fs.writeFileSync(htmlPath, html);

const chrome = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
execFileSync(
  chrome,
  [
    '--headless=new',
    '--disable-gpu',
    '--hide-scrollbars',
    '--window-size=1240,880',
    `--screenshot=${path.join(__dirname, 'brandmark-preview.png')}`,
    `file:///${htmlPath.replace(/\\/g, '/')}`,
  ],
  { stdio: 'ignore' },
);
console.log('ok');
