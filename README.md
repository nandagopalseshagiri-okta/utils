# utils

How to run jsgen to produce javascript from java code and test the output
```bash
./jsgen.sh JSSharedConstant.java | node
```

How to check if public key in code matches the cert from okta server (trexcloud, preview or prod)
```
./strings_in_class.sh com.okta.agent.ssl.OktaCertPublicKeys -cp ~/Downloads/OktaRsaSecurIdAgent.jar | grep `./pull_publickey.sh okta.okta.com:443`
```
Here the strings hard coded inside class OktaCertPublicKeys present in OktaRsaSecurIdAgent.jar is streamed to grep that is looking for public substituted into it's command line by execution of pull_publickey.sh
