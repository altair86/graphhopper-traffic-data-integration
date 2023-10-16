mvn -DskipTests=true install assembly:single

timeout 1

java -Dsun.misc.URLClassPath.disableJarChecking=true --add-opens jdk.naming.rmi/com.sun.jndi.rmi.registry=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.security.action=ALL-UNNAMED --add-opens java.base/sun.net=ALL-UNNAMED -jar target\traffic-demo-0.8.3-SNAPSHOT-web-assembly.jar datasource=kazakhstan-latest.osm.pbf
