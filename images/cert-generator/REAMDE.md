# HOW TO
1. Build
2. Create eidas.conf, eg.
```
[ req ]
distinguished_name = dn
prompt = no

[ dn ]
O=dummy
L=dummy
C=PL
organizationIdentifier=PSDPL-DUMMY-01234567
CN=dummy
```
3. Run
```
docker run -it -v ${DIR}:/tmp/cert-generator cert-generator 
```
where ${DIR} is directory containing eidas.conf
