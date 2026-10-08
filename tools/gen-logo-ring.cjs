// 一次性工具：生成"墨迹回旋"logo 的 SVG 路径。
// 思路：全用长而缓的锥形笔触（毛笔感），不要尖刺、不要断裂感。
// 结果固化进 frontend/components/BrandMark.tsx，改设计时重跑本脚本。
const fs = require('fs');
const path = require('path');

const CX = 384, CY = 384;
let seed = 20260214;
const rand = () => {
  seed = (seed * 1103515245 + 12345) % 2147483648;
  return seed / 2147483648;
};
const between = (a, b) => a + (b - a) * rand();
const r2 = (n) => Math.round(n * 10) / 10;

/** 极坐标点 */
const at = (angleDeg, radius) => {
  const a = (angleDeg * Math.PI) / 180;
  return [CX + radius * Math.cos(a), CY + radius * Math.sin(a)];
};

/** 点列 → 平滑三次贝塞尔（Catmull-Rom） */
function smooth(points, move) {
  let d = `${move ? 'M' : 'L'} ${r2(points[0][0])} ${r2(points[0][1])}`;
  for (let i = 0; i < points.length - 1; i += 1) {
    const p0 = points[i - 1] || points[i];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[i + 2] || p2;
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += ` C ${r2(c1[0])} ${r2(c1[1])} ${r2(c2[0])} ${r2(c2[1])} ${r2(p2[0])} ${r2(p2[1])}`;
  }
  return d;
}

/** 起笔/收笔形态：指数越小越饱满，只在两端快速收锋 */
const POWER = { sharp: 0.45, round: 1.3, soft: 0.75 };

/**
 * 一笔墨：中心线沿 Ark 走，线宽按正弦包络起收，
 * envelope 决定收笔的利落程度，width 是腰部最粗处。
 * 用中心线 + 偏移量构造外/内两条边，再各自平滑，得到毛笔式的粗细变化。
 */
function stroke({ from, to, radius, width, steps = 48, drift = 7, taper = 'sharp', belly = 0.5 }) {
  const outer = [];
  const inner = [];
  const pow = POWER[taper];
  for (let i = 0; i <= steps; i += 1) {
    const t = i / steps;
    const angle = from + (to - from) * t;
    let envelope = Math.pow(Math.sin(Math.PI * t), pow);
    // belly 越大，最粗处越靠后（像收笔前的那一顿）
    envelope *= 0.86 + belly * 0.5 * Math.sin(Math.PI * t);
    const halfWidth = Math.max(0.8, (width / 2) * envelope);
    const r = radius + Math.sin(t * Math.PI * 1.7) * drift;
    outer.push(at(angle, r + halfWidth));
    inner.push(at(angle, r - halfWidth));
  }
  const end = inner[inner.length - 1];
  return `${smooth(outer, true)} L ${r2(end[0])} ${r2(end[1])} ${smooth(inner.slice().reverse(), false)} Z`;
}

/** 由一串 [角度, 半径] 控制点插值出一条自由挥洒的中心线，再给宽度 */
function freehand({ points, width, steps = 60, taper = 'sharp', bulge = 0.55 }) {
  const [startAngle, startRadius] = points[0];
  const [endAngle, endRadius] = points[points.length - 1];
  const outer = [];
  const inner = [];
  const pow = POWER[taper];
  for (let i = 0; i <= steps; i += 1) {
    const t = i / steps;
    let angle = 0;
    let radius = 0;
    for (let k = 0; k < points.length - 1; k += 1) {
      const [a0, r0] = points[k];
      const [a1, r1] = points[k + 1];
      const lo = k / (points.length - 1);
      const hi = (k + 1) / (points.length - 1);
      if (t >= lo && t <= hi) {
        const local = (t - lo) / (hi - lo);
        // 平滑插值，避免折角
        const e = local * local * (3 - 2 * local);
        angle = a0 + (a1 - a0) * e;
        radius = r0 + (r1 - r0) * e;
        break;
      }
    }
    if (i === steps) {
      angle = endAngle;
      radius = endRadius;
    }
    const envelope = Math.pow(Math.sin(Math.PI * t), pow) * (0.8 + bulge * Math.sin(Math.PI * t));
    const halfWidth = Math.max(0.6, (width / 2) * envelope);
    outer.push(at(angle, radius + halfWidth));
    inner.push(at(angle, radius - halfWidth));
  }
  const end = inner[inner.length - 1];
  return `${smooth(outer, true)} L ${r2(end[0])} ${r2(end[1])} ${smooth(inner.slice().reverse(), false)} Z`;
}

/**
 * 一笔墨：中心线沿 [角度, 半径, 笔宽] 控制点平滑插值，
 * 线宽用"平台 + 两端快速收锋"的剖面，避免拉出细长的尖角。
 */
function spiral({ points, steps = 96, taper = 'soft' }) {
  const outer = [];
  const inner = [];
  const sample = (u) => {
    const n = points.length - 1;
    const scaled = u * n;
    const i = Math.min(n - 1, Math.floor(scaled));
    const local = scaled - i;
    const e = local * local * (3 - 2 * local);
    const [a0, r0, w0] = points[i];
    const [a1, r1, w1] = points[i + 1];
    return [a0 + (a1 - a0) * e, r0 + (r1 - r0) * e, w0 + (w1 - w0) * e];
  };
  for (let i = 0; i <= steps; i += 1) {
    const t = i / steps;
    const [angle, radius, width] = sample(t);
    const halfWidth = Math.max(1.4, (width / 2) * profile(t, taper));
    outer.push(at(angle, radius + halfWidth));
    inner.push(at(angle, radius - halfWidth));
  }
  const end = inner[inner.length - 1];
  return `${smooth(outer, true)} L ${r2(end[0])} ${r2(end[1])} ${smooth(inner.slice().reverse(), false)} Z`;
}

/** 宽度剖面：中段保持饱满，只在头尾各约一成处收锋 */
function profile(t, taper) {
  const ramp = taper === 'round' ? 0.16 : 0.11;
  const plateau = taper === 'round' ? 0.82 : 0.72;
  const value = Math.min(1, Math.min(t, 1 - t) / ramp);
  return Math.max(plateau, Math.pow(value, 0.6));
}

// ── 候选方案 ────────────────────────────────────────────────
// 一笔到底：从右上起笔，绕环一圈，收笔时顺着切向往外拖出一段墨尾 ——
// 不回头、不与起笔相交，只"甩开"，这样既不尖也不打结。
const ensō = (weight = 1) =>
  spiral({
    points: [
      [-88, 252, 10 * weight],
      [-74, 240, 34 * weight],
      [-48, 232, 56 * weight],
      [0, 226, 68 * weight],
      [40, 222, 72 * weight],
      [80, 221, 72 * weight],
      [120, 222, 72 * weight],
      [160, 226, 70 * weight],
      [190, 232, 64 * weight],
      [210, 244, 56 * weight],
      [230, 262, 50 * weight],
      [256, 284, 42 * weight],
      [288, 304, 32 * weight],
      [324, 318, 22 * weight],
      [358, 326, 10 * weight],
    ],
    steps: 150,
  });

/** 环内侧的一小段破锋：贴着主笔，取"墨分五色"的一点层次 */
const echo = (weight = 1) =>
  spiral({
    points: [
      [104, 258, 4 * weight],
      [144, 262, 12 * weight],
      [182, 262, 11 * weight],
      [210, 258, 5 * weight],
    ],
    steps: 44,
    taper: 'round',
  });

const variants = {
  // 定稿：一笔到底 + 内侧破锋
  A: [ensō(), echo()],
  // 备选：只要一笔（没有破锋）
  B: [ensō()],
  // 备选：笔更细、尾更长，更飘
  C: [ensō(0.88), echo(0.9)],
  // 备选：笔更实，尾稍短
  D: [ensō(1.12)],
};

// 预览网格：把四个候选并排渲染成一张图（含环内字标，方便看真实观感）
const cell = 400;
const cells = Object.entries(variants)
  .map(([name, paths], index) => {
    const x = (index % 2) * cell;
    const y = Math.floor(index / 2) * cell;
    const scale = cell / 860;
    return `<g transform="translate(${x},${y}) scale(${scale})">
      <g transform="translate(-10,-30)">
        <g fill="#1c1917">${paths.map((d) => `<path d="${d}"/>`).join('')}</g>
        <text x="394" y="404" text-anchor="middle" font-family="'Segoe UI',sans-serif" font-size="68" font-weight="600" letter-spacing="4" fill="#1c1917">InkSpace</text>
        <text x="394" y="820" text-anchor="middle" font-family="sans-serif" font-size="34" fill="#a8a29e">${name}</text>
      </g>
    </g>`;
  })
  .join('\n');

const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${cell * 2} ${cell * 2}" width="820" height="820">${cells}</svg>`;
fs.writeFileSync(path.join(__dirname, 'logo-variants.svg'), svg);

// 最终尺寸的候选文件，方便逐个看
for (const [name, paths] of Object.entries(variants)) {
  fs.writeFileSync(path.join(__dirname, `logo-variant-${name}.txt`), paths.join('\n'));
}
// 定稿候选：写进 logo-ring.txt 供 sync-logo.cjs 同步进组件与 SVG
fs.writeFileSync(path.join(__dirname, 'logo-ring.txt'), variants.A.join('\n'));

// 小尺寸检查：36px 的侧栏图标还认不认得出，是这套笔触的主要风险点
const marks = [220, 96, 44, 36, 24]
  .map(
    (size, index) =>
      `<g transform="translate(${index * 240 + 20},20) scale(${size / 768})">
        <g fill="#1c1917">${variants.A.map((d) => `<path d="${d}"/>`).join('')}</g>
      </g>`,
  )
  .join('\n');
fs.writeFileSync(
  path.join(__dirname, 'logo-sizes.svg'),
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1240 260" width="1240" height="260"><rect width="1240" height="260" fill="#faf8f5"/>${marks}</svg>`,
);

console.log('wrote logo-variants.svg, logo-sizes.svg, logo-ring.txt and 4 variant files');
