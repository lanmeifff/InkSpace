/** @type {import('next').NextConfig} */
const nextConfig = {
  // 容器部署用 standalone 产物，运行阶段不需要完整 node_modules
  output: 'standalone',
};

export default nextConfig;
