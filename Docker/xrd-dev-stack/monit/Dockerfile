FROM alpine:latest

RUN apk add --no-cache monit bash

# Monit web interface port (default: 2812)
EXPOSE 2812

CMD ["monit", "-I"]
