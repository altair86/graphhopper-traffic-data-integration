package com.graphhopper.traffic.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import gnu.trove.set.hash.TIntHashSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich
 */
@Singleton
public class DataUpdater {

    private static final String ROAD_DATA_URL = "http://www.stadt-koeln.de/externe-dienste/open-data/traffic.php";
    @Inject
    private GraphHopper hopper;

    @Inject
    private ObjectMapper objectMapper;

    private final OkHttpClient client;
    
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final java.util.logging.Logger loggerHistoryLL = java.util.logging.Logger.getLogger("HistoryLL");  
    
    private final Lock writeLock;
    private final long seconds = 150;
    private RoadData currentRoads;
    java.util.logging.FileHandler fh;  

    /**
     *
     * @param writeLock
     */
    public DataUpdater(Lock writeLock) {
    	
        this.writeLock = writeLock;
        client = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build();
        
        try {
            // 10 files on 100 kB - its about 30,000 poinst with speed
            fh = new java.util.logging.FileHandler("log_history_ll/points.log", 100000, 10, true);
            
            loggerHistoryLL.addHandler(fh);

            FormatterForLL formatter = new FormatterForLL();
            fh.setFormatter(formatter);
        } catch (FileNotFoundException e) {
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
    }
    
    public void loadLogHistoryLL() {
        List<String> fileNames = new ArrayList<String>();
        
        try {
            File folder = new File("log_history_ll");
            if (folder.exists()) {
                for (final File fileEntry : folder.listFiles()) {
                    if (!fileEntry.isDirectory()) {
                        String fileName = fileEntry.getName();
                        if (fileName.contains("points.log.") && !fileName.contains("lck")) {
                            logger.info(fileEntry.getName());
                            fileNames.add(fileName);
                        }
                    }
                }
            }

            Collections.sort(fileNames, (String o1, String o2) -> {
                int i = Integer.valueOf(o1.replace("points.log.", ""))
                        .compareTo(Integer.valueOf(o2.replace("points.log.", "")));
                if (i != 0) return -i;

                return 0;   
            });
        } 
        catch(Exception e) {
              e.printStackTrace();
        }
        
        logger.info("loading fileNames: " + fileNames);
        
        for (String fn : fileNames) {
            try {
                Scanner in = new Scanner(new File("log_history_ll/" + fn));

                while (in.hasNextLine()) {
                    String line = in.nextLine();

                    String[] data = line.split(" ");
                    if (data.length == 3) {
                        List<Point> points = new ArrayList<Point>();
                        points.add(
                                                                new Point(Double.parseDouble(data[0]),
                                                                            Double.parseDouble(data[1]))
                                        );
                        RoadData rd = new RoadData();
                        rd.add(new RoadEntry("1", 
                                        points, 
                                        Double.parseDouble(data[2]),
                                        "speed",
                                        "replace")
                                );
                        feed(rd, false);
                    }
                }
            }
            catch(FileNotFoundException e){
                e.printStackTrace();
            }
            catch(Exception e) {
                  e.printStackTrace();
            }
        }
    }

    public void feed(RoadData data, boolean saveHistory) {
        writeLock.lock();
        try {
            lockedFeed(data, saveHistory);
        } finally {
            writeLock.unlock();
        }
    }

    private void lockedFeed(RoadData data, boolean saveHistory) {
        currentRoads = data;
        Graph graph = hopper.getGraphHopperStorage();
        
        FlagEncoder carEncoder = hopper.getEncodingManager().getEncoder("car");
        LocationIndex locationIndex = hopper.getLocationIndex();

        int errors = 0;
        int updates = 0;
        TIntHashSet edgeIds = new TIntHashSet(data.size());
        for (RoadEntry entry : data) {

            // TODO get more than one point -> our map matching component
            Point point = entry.getPoints().get(entry.getPoints().size() / 2);
            QueryResult qr = locationIndex.findClosest(point.lat, point.lon, EdgeFilter.ALL_EDGES);
            if (!qr.isValid()) {
                // logger.info("no matching road found for entry " + entry.getId() + " at " + point);
                errors++;
                continue;
            }

            int edgeId = qr.getClosestEdge().getEdge();
            if (edgeIds.contains(edgeId)) {
                // TODO this wouldn't happen with our map matching component
                errors++;
                continue;
            }

            edgeIds.add(edgeId);
            EdgeIteratorState edge = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            double value = entry.getValue();
            if ("replace".equalsIgnoreCase(entry.getMode())) {
                if ("speed".equalsIgnoreCase(entry.getValueType())) {
                    double oldSpeed = carEncoder.getSpeed(edge.getFlags());
                    if (oldSpeed != value) {
                        updates++;
                        // TODO use different speed for the different directions (see e.g. Bike2WeightFlagEncoder)
                        logger.info("Speed change at " + entry.getId() + " (" + point + "). Old: " + oldSpeed + ", new:" + value);
                        if (saveHistory) {
                            saveHistoryLL(point.lat, point.lon, value);
                        }
                        edge.setFlags(carEncoder.setSpeed(edge.getFlags(), value));
                    }
                } else {
                    throw new IllegalStateException("currently no other value type than 'speed' is supported");
                }
            } else {
                throw new IllegalStateException("currently no other mode than 'replace' is supported");
            }
        }

        logger.info("Updated " + updates + " street elements of " + data.size() + ". Unchanged:" + (data.size() - updates) + ", errors:" + errors);
    }

    /*
     * Save history about points of traffic 
     * @param lat
     * @param lon
     * @param spd 
     */
     public void saveHistoryLL(double lat, double lon, double spd) {
        new Thread("saveHistoryLL" + lat + lon) {
            @Override
            public void run() {
                try {
                    loggerHistoryLL.info(lat + " " + lon + " " + spd );
                } catch (Exception ex) {
                    logger.error("Problem while saveHistoryLL", ex);
                }
            }
        }.start();
    }
     
    protected String fetchJSONString(String url) throws IOException {
        Request okRequest = new Request.Builder().url(url).build();
        return client.newCall(okRequest).execute().body().string();
    }

    public RoadData fetchTrafficData(String url) throws IOException {
        final String trafficJsonString = fetchJSONString(url);
        final OpenTrafficData trafficData = objectMapper.readValue(trafficJsonString, OpenTrafficData.class);
        RoadData data = new RoadData();

        for (final TrafficFeature trafficFeature : trafficData.features) {
            final String idStr = trafficFeature.attributes.identifier;
            final int streetUsage = trafficFeature.attributes.auslastung;

            // according to the docs http://www.offenedaten-koeln.de/dataset/verkehrskalender-der-stadt-k%C3%B6ln
            // there are only three indications 0='ok', 1='slow' and 2='traffic jam'
            if (streetUsage != 0 && streetUsage != 1 && streetUsage != 2) {
                continue;
            }

            final double speed;
            if (streetUsage == 1) {
                speed = 20;
            } else if (streetUsage == 2) {
                speed = 5;
            } else {
                // If there is a traffic jam we need to revert afterwards!
                speed = 45; // TODO getOldSpeed();
            }

            final List<List<List<Double>>> paths = trafficFeature.geometry.paths;
            for (int pathPointIndex = 0; pathPointIndex < paths.size(); pathPointIndex++) {
                final List<Point> points = new ArrayList<>();
                final List<List<Double>> path = paths.get(pathPointIndex);
                for (int pointIndex = 0; pointIndex < path.size(); pointIndex++) {
                    final List<Double> point = path.get(pointIndex);
                    points.add(new Point(point.get(1), point.get(0)));
                }

                if (!points.isEmpty()) {
                    data.add(new RoadEntry(idStr + "_" + pathPointIndex, points, speed, "speed", "replace"));
                }
            }

        }

        return data;
    }

    private final AtomicBoolean running = new AtomicBoolean(false);

    public void start() {
        if (running.get()) {
            return;
        }

        running.set(true);
        new Thread("DataUpdater" + seconds) {
            @Override
            public void run() {
                logger.info("fetch new data every " + seconds + " seconds");
                while (running.get()) {
                    try {
                        logger.info("fetch new data");
                        RoadData data = fetchTrafficData(ROAD_DATA_URL);
                        feed(data, true);
                        try {
                            Thread.sleep(seconds * 1000);
                        } catch (InterruptedException ex) {
                            logger.info("update thread stopped");
                            break;
                        }
                    } catch (Exception ex) {
                        logger.error("Problem while fetching data", ex);
                    }
                }
            }
        }.start();
    }

    public void stop() {
        running.set(false);
    }

    public RoadData getAll() {
        if (currentRoads == null) {
            return new RoadData();
        }

        return currentRoads;
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class OpenTrafficData {
        @JsonProperty("features")
        public List<TrafficFeature> features;
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class TrafficFeature {
        @JsonProperty("attributes")
        public TrafficAttributes attributes;

        @JsonProperty("geometry")
        public TrafficGeometry geometry;
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class TrafficAttributes {
        @JsonProperty("IDENTIFIER")
        public String identifier;

        @JsonProperty("AUSLASTUNG")
        public Integer auslastung;
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class TrafficGeometry {
        @JsonProperty("paths")
        public List<List<List<Double>>> paths;
    }
}
