// Direct API call to superdesign - bypasses shell/argv mangling
// POST /external/drafts/{draftId}/flow/execute with Bearer auth

const fs = require('fs');
const path = require('path');
const https = require('https');

const configPath = path.join(process.env.USERPROFILE, '.superdesign', 'config.json');
const config = JSON.parse(fs.readFileSync(configPath, 'utf-8'));
const apiKey = config.apiKey;
const teamId = config.teamId;
const baseHost = 'api.superdesign.dev';
const basePath = '/v1';
const draftId = 'b35ee64d-c44d-4033-a574-f9545025a5e7';

const pagesPath = path.resolve(__dirname, 'flow-pages-batch.json');
const pages = JSON.parse(fs.readFileSync(pagesPath, 'utf-8').trim());

const themePath = path.resolve(__dirname, 'init', 'theme.md');
const designSystemPath = path.resolve(__dirname, 'design-system.md');
const contextFiles = [
  { filename: '.superdesign/init/theme.md', content: fs.readFileSync(themePath, 'utf-8') },
  { filename: '.superdesign/design-system.md', content: fs.readFileSync(designSystemPath, 'utf-8') },
];

const body = JSON.stringify({
  flowContext: undefined,
  pages,
  contextFiles,
  referenceIds: undefined,
  model: undefined,
});

const url = `${basePath}/external/drafts/${draftId}/flow/execute`;
console.log(`[direct] POST https://${baseHost}${url}`);
console.log(`[direct] teamId=${teamId} pages=${pages.length} contextFiles=${contextFiles.length}`);

const req = https.request({
  host: baseHost,
  port: 443,
  path: url,
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(body),
    'Authorization': `Bearer ${apiKey}`,
    'X-Team-Id': teamId,
    'X-Install-Id': config.installId,
    'User-Agent': 'mindanchor-superdesign-direct/1.0',
  },
  timeout: 300000,
}, (res) => {
  console.log(`[direct] status: ${res.statusCode}`);
  console.log(`[direct] headers: ${JSON.stringify(res.headers)}`);
  let chunks = [];
  res.on('data', (c) => chunks.push(c));
  res.on('end', () => {
    const out = Buffer.concat(chunks).toString('utf-8');
    const logPath = path.resolve(__dirname, 'flow-pages-result.log');
    fs.writeFileSync(logPath, out, 'utf-8');
    console.log(`[direct] wrote ${out.length} bytes to ${logPath}`);
    console.log(out.slice(0, 2000));
  });
});

req.on('error', (e) => {
  console.error('[direct] request error:', e.message);
  process.exit(2);
});

req.on('timeout', () => {
  console.error('[direct] request timeout');
  req.destroy();
  process.exit(3);
});

req.write(body);
req.end();
