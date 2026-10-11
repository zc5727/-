# =========================================================
# 前端镜像：纯静态托管，无需构建步骤
#   依赖（Vue3 / Element Plus / ECharts）已本地化到 assets/vendor，
#   因此镜像构建不需要 npm，也不需要访问外网 CDN。
# =========================================================
FROM nginx:1.25-alpine

ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
 && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo "Asia/Shanghai" > /etc/timezone

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY index.html /usr/share/nginx/html/index.html
COPY assets   /usr/share/nginx/html/assets

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1
