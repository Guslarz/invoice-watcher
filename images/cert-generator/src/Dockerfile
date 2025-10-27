FROM ubuntu:25.10

WORKDIR /tmp/cert-generator

RUN apt update
RUN apt install openssl

CMD openssl req -new -config eidas.conf -keyout eidas.key -out eidas.csr && \
    openssl x509 -req -in eidas.csr -signkey eidas.key -out eidas.crt
