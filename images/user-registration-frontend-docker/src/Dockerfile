FROM nginx:1.30.0-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY browser/ /usr/share/nginx/html/
COPY 3rdpartylicenses.txt /usr/share/nginx/html/3rdpartylicenses.txt
ARG PROJECT_VERSION
ARG BUILD_DATE
LABEL org.opencontainers.image.version="${PROJECT_VERSION}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"
EXPOSE 8080
