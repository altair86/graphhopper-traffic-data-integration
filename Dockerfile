FROM openjdk:19

ADD ./target/traffic-demo-0.8.3-SNAPSHOT-web-assembly.jar ./target/traffic-demo-0.8.3-SNAPSHOT-web-assembly.jar
ADD td.sh td.sh
ADD config.properties config.properties

RUN mkdir /data

CMD ls /data && ./td.sh datasource=/data/map.osm.pbf