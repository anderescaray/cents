#!/usr/bin/env node
/*
 * Renders the reel.
 *
 *   node showreel/render.cjs stills 0.5,2.0,7.25        one PNG per instant, for review
 *   node showreel/render.cjs cues                         writes showreel/build/cues.json
 *   node showreel/render.cjs video [--samples 6]          the film: frames, motion blur, H.264 + AAC
 *
 * Frames come from headless Chromium. Motion blur is real: every output frame is the average of
 * `samples` renders spread across a 180-degree shutter, which `render(t)` allows because it is a
 * pure function of time. The soundtrack is `soundtrack.py`, synthesised from the cue list the
 * picture exports, so a hit on screen and a hit in the mix are the same number.
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn, execSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(__dirname, 'build');

function loadPlaywright() {
  try {
    return require('playwright');
  } catch (_) {
    const globalRoot = execSync('npm root -g').toString().trim();
    return require(path.join(globalRoot, 'playwright'));
  }
}

function findFfmpeg() {
  if (process.env.FFMPEG) return process.env.FFMPEG;
  try {
    execSync('ffmpeg -version', { stdio: 'ignore' });
    return 'ffmpeg';
  } catch (_) {
    /* fall through */
  }
  try {
    return execSync('python3 -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())"').toString().trim();
  } catch (_) {
    throw new Error('No ffmpeg with libx264: set FFMPEG, or `pip install imageio-ffmpeg`.');
  }
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.ttf': 'font/ttf',
  '.json': 'application/json',
  '.png': 'image/png',
};

/** The page loads the font from the app's own resources, so the server's root is the repo. */
function serve() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]));
      if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
        res.writeHead(404);
        res.end();
        return;
      }
      res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
      fs.createReadStream(file).pipe(res);
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

function args() {
  const [mode = 'video', ...rest] = process.argv.slice(2);
  const opts = { mode, positional: [] };
  for (let i = 0; i < rest.length; i++) {
    if (rest[i].startsWith('--')) opts[rest[i].slice(2)] = rest[i + 1] && !rest[i + 1].startsWith('--') ? rest[++i] : true;
    else opts.positional.push(rest[i]);
  }
  return opts;
}

async function openPage(browser, port, scale = 1) {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: scale });
  page.on('pageerror', (e) => console.error('[page error]', e.message));
  page.on('console', (m) => {
    if (m.type() === 'error' || m.type() === 'warning') console.error(`[page ${m.type()}]`, m.text());
  });
  await page.goto(`http://127.0.0.1:${port}/showreel/reel.html?render`);
  await page.evaluate(() => window.reelReady);
  const cdp = await page.context().newCDPSession(page);
  return { page, cdp };
}

async function capture({ page, cdp }, t) {
  await page.evaluate((at) => window.renderFrame(at), t);
  const { data } = await cdp.send('Page.captureScreenshot', { format: 'png', optimizeForSpeed: true });
  return Buffer.from(data, 'base64');
}

function writeTo(stream, buf) {
  return new Promise((resolve) => {
    if (stream.write(buf)) resolve();
    else stream.once('drain', resolve);
  });
}

async function main() {
  const opts = args();
  const { chromium } = loadPlaywright();
  fs.mkdirSync(OUT, { recursive: true });
  const server = await serve();
  const port = server.address().port;
  const browser = await chromium.launch({
    args: ['--font-render-hinting=none', '--disable-lcd-text', '--force-color-profile=srgb', '--hide-scrollbars'],
  });

  try {
    const probe = await openPage(browser, port);
    const info = await probe.page.evaluate(() => ({ duration: window.reelInfo.duration, cues: window.reelInfo.cues }));
    fs.writeFileSync(path.join(OUT, 'cues.json'), JSON.stringify(info, null, 1));

    if (opts.mode === 'cues') {
      console.log(`${info.cues.length} cues → build/cues.json`);
      return;
    }

    if (opts.mode === 'stills') {
      const dir = path.join(OUT, 'stills');
      fs.mkdirSync(dir, { recursive: true });
      const scale = opts.scale ? parseFloat(opts.scale) : 1;
      const shot = scale === 1 ? probe : await openPage(browser, port, scale);
      const times = opts.positional.join(',').split(',').filter(Boolean).map(Number);
      for (const t of times) {
        const file = path.join(dir, `t${t.toFixed(3).padStart(7, '0')}.png`);
        fs.writeFileSync(file, await capture(shot, t));
        console.log(file);
      }
      return;
    }

    if (opts.mode === 'video') {
      const ffmpeg = findFfmpeg();
      const fps = parseInt(opts.fps || '60', 10);
      const samples = parseInt(opts.samples || '6', 10);
      const shutter = parseFloat(opts.shutter || '0.5');
      const workers = parseInt(opts.workers || '4', 10);
      const total = Math.round(info.duration * fps);
      const first = parseInt(opts.from || '0', 10);
      const last = opts.to ? parseInt(opts.to, 10) : total;
      const framesDir = path.join(OUT, 'frames');
      fs.mkdirSync(framesDir, { recursive: true });

      const chunk = Math.ceil((last - first) / workers);
      const started = Date.now();
      let done = 0;
      await Promise.all(
        Array.from({ length: workers }, async (_, w) => {
          const f0 = first + w * chunk;
          const f1 = Math.min(last, f0 + chunk);
          if (f0 >= f1) return;
          const tab = w === 0 ? probe : await openPage(browser, port);
          const filters =
            samples > 1
              ? ['-vf', `tmix=frames=${samples}:weights=${Array(samples).fill(1).join(' ')},select='eq(mod(n\\,${samples})\\,${samples - 1})'`]
              : [];
          const ff = spawn(
            ffmpeg,
            ['-hide_banner', '-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', String(fps * samples), '-i', '-',
              ...filters, '-fps_mode', 'passthrough', '-start_number', String(f0), path.join(framesDir, 'f_%05d.png')],
            { stdio: ['pipe', 'inherit', 'inherit'] },
          );
          const finished = new Promise((resolve, reject) => {
            ff.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`ffmpeg exited ${code}`))));
            ff.stdin.on('error', reject);
          });
          for (let n = f0; n < f1; n++) {
            for (let k = 0; k < samples; k++) {
              const offset = samples > 1 ? ((k + 0.5) / samples - 0.5) * (shutter / fps) : 0;
              const t = Math.min(Math.max(n / fps + offset, 0), info.duration - 1e-4);
              await writeTo(ff.stdin, await capture(tab, t));
            }
            done++;
            if (done % 30 === 0) {
              const rate = done / ((Date.now() - started) / 1000);
              process.stdout.write(`\r${done}/${last - first} frames · ${rate.toFixed(1)} fps · eta ${((last - first - done) / rate).toFixed(0)} s   `);
            }
          }
          ff.stdin.end();
          await finished;
        }),
      );
      process.stdout.write('\n');

      if (opts['no-encode']) return;
      const audio = path.join(OUT, 'soundtrack.wav');
      const hasAudio = fs.existsSync(audio) && !opts.silent;
      const out = path.resolve(opts.out || path.join(__dirname, 'cents-showreel.mp4'));
      const encode = [
        '-hide_banner', '-loglevel', 'error', '-y',
        '-framerate', String(fps), '-i', path.join(framesDir, 'f_%05d.png'),
        ...(hasAudio ? ['-i', audio] : []),
        '-vf', 'scale=out_color_matrix=bt709:out_range=tv,format=yuv420p',
        '-c:v', 'libx264', '-preset', 'slow', '-crf', String(opts.crf || 17), '-profile:v', 'high',
        '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
        ...(hasAudio ? ['-c:a', 'aac', '-b:a', '256k', '-shortest'] : []),
        '-movflags', '+faststart', out,
      ];
      execSync([ffmpeg, ...encode].map((a) => (/[\s'()=,:]/.test(a) ? `"${a}"` : a)).join(' '), { stdio: 'inherit' });
      console.log(`${out}${hasAudio ? '' : ' (no soundtrack yet)'}`);
    }
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
