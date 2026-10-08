// 一次性工具：把 tools/logo-ring.txt（gen-logo-ring.cjs 的输出）同步进
// frontend/components/BrandMark.tsx 与 frontend/public/logo.svg，保证两处图形一致。
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const paths = fs
  .readFileSync(path.join(__dirname, 'logo-ring.txt'), 'utf8')
  .split('\n')
  .map((line) => line.trim())
  .filter(Boolean);

const component = `/**
 * 品牌标记：墨迹环（一笔回旋的锥形笔触 + 分叉）+ 环内 "InkSpace" 字标。
 * 路径由 tools/gen-logo-ring.cjs 生成后固化在这里，避免运行时计算。
 */
const RING = [
${paths.map((d) => `  '${d}',`).join('\n')}
];

/**
 * @param compact 只画图形（窄屏顶栏用），不画字标
 * @param className 控制尺寸，例如 "h-9 w-9"
 */
export function InkMark({ compact = false, className }: { compact?: boolean; className?: string }) {
  return (
    <svg viewBox="0 0 768 768" className={className} role="img" aria-label="InkSpace 墨记">
      <g fill="currentColor">
        {RING.map((d) => (
          <path key={d.slice(0, 24)} d={d} />
        ))}
      </g>
      {!compact && (
        <text
          x="384"
          y="396"
          textAnchor="middle"
          fontSize="74"
          fontWeight="600"
          letterSpacing="4"
          fill="currentColor"
        >
          InkSpace
        </text>
      )}
    </svg>
  );
}
`;

const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 768 768" width="768" height="768" role="img" aria-labelledby="inkspace-title">
  <title id="inkspace-title">InkSpace 墨记</title>
  <g fill="#1c1917">
${paths.map((d) => `    <path d="${d}"/>`).join('\n')}
    <text x="384" y="396" text-anchor="middle" font-family="ui-sans-serif, -apple-system, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif" font-size="74" font-weight="600" letter-spacing="4" fill="#1c1917">InkSpace</text>
  </g>
</svg>
`;

fs.writeFileSync(path.join(root, 'frontend', 'components', 'BrandMark.tsx'), component);
fs.writeFileSync(path.join(root, 'frontend', 'public', 'logo.svg'), svg);
console.log('synced', paths.length, 'paths');
