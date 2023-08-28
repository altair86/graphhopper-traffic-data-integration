#!/bin/bash

JAR=$(ls target/traffic-demo-*-web-assembly.jar)

if [ "$JAVA" = "" ]; then
 JAVA=java
fi

if [ ! -f "$JAR" ]; then
  mvn -DskipTests=true install assembly:single
  JAR=$(ls target/traffic-demo-*-web-assembly.jar)
fi

exec "$JAVA" $JAVA_OPTS -Dsun.misc.URLClassPath.disableJarChecking=true --add-opens jdk.naming.rmi/com.sun.jndi.rmi.registry=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.security.action=ALL-UNNAMED --add-opens java.base/sun.net=ALL-UNNAMED -jar $JAR "$@"