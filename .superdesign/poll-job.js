// Poll superdesign job status until terminal
const fs = require('fs');
const path = require('path');
const https = require('https');

const configPath = path.join(process.env.USERPROFILE, '.superdesign', 'config.json');
const config = JSON.parse(fs.readFileSync(configPath, 'utf-8'));
const apiKey = config.apiKey;
const teamId = config.teamId;
const baseHost = 'api.superdesign.dev';
const basePath = '/v1';

const jobId = process.argv[2];
if (!jobId) {
  console.error('Usage: node poll-job.js <jobId>');
  process.exit(1);
}

const timeoutMs = 8 * 60 * 1000; // 8 min (2 min/page * 4 pages)
const pollIntervalMs = 5000;
const start = Date.now();

function pollOnce() {
  return new Promise((resolve, reject) => {
    const url = `${basePath}/external/jobs/${jobId}`;
    const req = https.request({
      host: baseHost, port: 443, path: url, method: 'GET',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'X-Team-Id': teamId,
        'X-Install-Id': config.installId,
        'User-Agent': 'mindanchor-superdesign-direct/1.0',
      },
      timeout: 30000,
    }, (res) => {
      let chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const body = Buffer.concat(chunks).toString('utf-8');
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { resolve(JSON.parse(body)); }
          catch (e) { reject(new Error(`Non-JSON response: ${body}`)); }
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${body}`));
        }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    req.end();
  });
}

async function loop() {
  console.log(`[poll] jobId=${jobId} timeoutMs=${timeoutMs}`);
  while (Date.now() - start < timeoutMs) {
    let r;
    try { r = await pollOnce(); }
    catch (e) {
      console.error(`[poll] error: ${e.message}, retrying...`);
      await new Promise((res) => setTimeout(res, pollIntervalMs));
      continue;
    }
    const elapsed = Math.round((Date.now() - start) / 1000);
    console.log(`[poll] t+${elapsed}s status=${r.status || r.state || JSON.stringify(r).slice(0, 200)}`);
    if (r.status === 'completed' || r.status === 'complete' || r.status === 'done' || r.status === 'success') {
      const logPath = path.resolve(__dirname, 'flow-pages-result.log');
      fs.writeFileSync(logPath, JSON.stringify(r, null, 2), 'utf-8');
      console.log(`[poll] DONE. Result written to ${logPath}`);
      console.log(`[poll] jobId: ${r.jobId || jobId}`);
      if (r.result) {
        console.log(`[poll] result.drafts: ${JSON.stringify(r.result.drafts, null, 2).slice(0, 4000)}`);
        console.log(`[poll] result.projectUrl: ${r.result.projectUrl}`);
        console.log(`[poll] result.creditsConsumed: ${r.result.creditsConsumed}`);
      }
      return;
    }
    if (r.status === 'failed' || r.status === 'error') {
      console.error(`[poll] FAILED. ${JSON.stringify(r, null, 2)}`);
      process.exit(4);
    }
    await new Promise((res) => setTimeout(res, pollIntervalMs));
  }
  console.error(`[poll] TIMEOUT after ${timeoutMs}ms`);
  process.exit(5);
}

loop().catch((e) => { console.error(e); process.exit(6); });
