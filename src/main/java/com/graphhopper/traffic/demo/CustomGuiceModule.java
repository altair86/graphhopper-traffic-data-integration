package com.graphhopper.traffic.demo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.name.Names;
import com.graphhopper.http.GraphHopperModule;
import com.graphhopper.util.CmdArgs;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich
 */
public class CustomGuiceModule extends GraphHopperModule {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public CustomGuiceModule(CmdArgs args) {
        super(args);
    }

    @Override
    protected void configure() {
        super.configure();

        final DataUpdater updater = new DataUpdater(lock.writeLock());
        bind(DataUpdater.class).toInstance(updater);
        // start update thread
        //TODO disable auto fetching new data, because I dont need it
        //updater.start();
        
        //Try to load Log History LL from files
        //There is ThreadSleep because Graphhopper now not ready
        new Thread("loadLogHistoryLL") {
            @Override
            public void run() {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException ex) {
                    java.util.logging.Logger.getLogger(CustomGuiceModule.class.getName()).log(Level.SEVERE, null, ex);
                }
               updater.loadLogHistoryLL();
            }            
        }.start();
        
        ObjectMapper prettyOM = createMapper();
        prettyOM.enable(SerializationFeature.INDENT_OUTPUT);
        bind(ObjectMapper.class).annotatedWith(Names.named("prettyprint")).toInstance(prettyOM);
    }

    public static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // json is underscore!
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

        SimpleModule pointModule = new SimpleModule("PointModule");
        pointModule.addSerializer(Point.class, new PointSerializer());
        pointModule.addDeserializer(Point.class, new PointDeserializer());
        mapper.registerModule(pointModule);
        return mapper;
    }

    static class PointDeserializer extends JsonDeserializer<Point> {

        @Override
        public Point deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            JsonNode node = jp.getCodec().readTree(jp);
            Iterator<JsonNode> iter = node.elements();
            double lon = iter.next().asDouble();
            double lat = iter.next().asDouble();
            return new Point(lat, lon);
        }
    }

    static class PointSerializer extends JsonSerializer<Point> {

        @Override
        public void serialize(Point value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            // geojson
            jgen.writeStartArray();
            jgen.writeNumber(value.lon);
            jgen.writeNumber(value.lat);
            jgen.writeEndArray();
        }
    }
}
