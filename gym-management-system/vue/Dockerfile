# 前端：Vue 2.6 + ElementUI —— 构建后由 Nginx 托管
FROM node:16-alpine AS build
WORKDIR /build

# 国内 npm 镜像，避免拉包超时
RUN npm config set registry https://registry.npmmirror.com

COPY package.json package-lock.json ./
RUN npm install --legacy-peer-deps

COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.25-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
