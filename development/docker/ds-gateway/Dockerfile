FROM nginxinc/nginx-unprivileged:stable-alpine

USER root
COPY nginx.conf /etc/nginx/nginx.conf
COPY 40-ds-gateway-stream.sh /docker-entrypoint.d/40-ds-gateway-stream.sh
RUN chmod +x /docker-entrypoint.d/40-ds-gateway-stream.sh \
    && mkdir -p /etc/nginx/stream.d \
    && chown nginx:nginx /etc/nginx/stream.d
USER nginx
