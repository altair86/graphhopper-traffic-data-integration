cd /home/x211/graphhopper-traffic-data-integration

wget -O https://download.geofabrik.de/asia/kazakhstan-latest.osm.pbf

nohup ./td.sh datasource=kazakhstan-latest.osm.pbf &


### #Подгрузка данных трафика с 131

### cd /scripts/sh
### sh extract_traffic_data.sh
### cd /scripts/data
### 7z a extract_traffic_data.7z extract_traffic_data.json

### #После надо куда надо файл загрузить


#Загрузка nолученного файла в GH для трафика
curl -H "Content-Type: application/json" -T extract_traffic_data.json -X POST http://localhost:8989/datafeed