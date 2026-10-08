// 一次性工具：生成墨迹环形 logo 的 SVG 路径（一笔圆锥形回旋笔触 + 外伸分叉），
// 结果已固化进 frontend/components/BrandMark.tsx，改设计时重跑本脚本。
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

/** 把点列转成平滑的 SVG 路径（Catmull-Rom → 三次贝塞尔） */
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

/**
 * 一笔环形笔触：沿圆弧走 330°，半径与线宽都随机微抖，
 * 线宽按正弦包络在两端收成尖，中间最厚 —— 出"毛笔回旋"的手感。
 */
function sweep({ from, to, radius, width, steps }) {
  const outer = [];
  const inner = [];
  for (let i = 0; i <= steps; i += 1) {
    const t = i / steps;
    const angle = from + (to - from) * t;
    const envelope = Math.pow(Math.sin(Math.PI * t), 0.55);
    const halfWidth = Math.max(1.5, (width / 2) * envelope) * between(0.82, 1.18);
    const r = radius + between(-7, 7);
    outer.push(at(angle, r + halfWidth));
    inner.push(at(angle, r - halfWidth));
  }
  const end = inner[inner.length - 1];
  return `${smooth(outer, true)} L ${r2(end[0])} ${r2(end[1])} ${smooth(inner.slice().reverse(), false)} Z`;
}

/** 从环上某点向外伸出的分叉：根部粗、尖端细 */
function branch(angleDeg, length, base) {
  const [x, y] = at(angleDeg, 238);
  const a = (angleDeg * Math.PI) / 180 + between(-0.3, 0.3);
  const bend = between(-30, 30);
  const tip = [x + Math.cos(a) * length, y + Math.sin(a) * length];
  const mid = [
    x + Math.cos(a) * length * 0.55 + Math.cos(a + Math.PI / 2) * bend,
    y + Math.sin(a) * length * 0.55 + Math.sin(a + Math.PI / 2) * bend,
  ];
  const nx = Math.cos(a + Math.PI / 2) * base;
  const ny = Math.sin(a + Math.PI / 2) * base;
  return [
    `M ${r2(x + nx)} ${r2(y + ny)}`,
    `Q ${r2(mid[0] + nx * 0.4)} ${r2(mid[1] + ny * 0.4)} ${r2(tip[0])} ${r2(tip[1])}`,
    `Q ${r2(mid[0] - nx * 0.5)} ${r2(mid[1] - ny * 0.5)} ${r2(x - nx * 0.9)} ${r2(y - ny * 0.9)}`,
    'Z',
  ].join(' ');
}

const paths = [
  sweep({ from: -58, to: 205, radius: 232, width: 42, steps: 48 }),
  // 断开的细弧，补出墨迹的层次
  sweep({ from: 14, to: 76, radius: 238, width: 12, steps: 10 }),
  sweep({ from: 300, to: 330, radius: 246, width: 10, steps: 8 }),
  // 开口处的一小段：让"缺口"像有意留白，而不是忘了画
  sweep({ from: 336, to: 6, radius: 244, width: 16, steps: 10 }),
];

for (const angle of [100, 154, 200, 248, 300]) {
  paths.push(branch(angle, between(52, 96), between(11, 16)));
}

fs.writeFileSync(path.join(__dirname, 'logo-ring.txt'), paths.join('\n'));
console.log(paths.length, 'paths written');
