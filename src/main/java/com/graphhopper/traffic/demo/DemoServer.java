package com.graphhopper.traffic.demo;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceFilter;
import com.graphhopper.http.GHServer;
import com.graphhopper.http.GraphHopperServletModule;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoServer {

    public static void main(String[] strArgs) throws Exception {
        CmdArgs args = CmdArgs.readFromConfig("config.properties", "graphhopper.config");
        // command line overwrite configs of properties file
        args.merge(CmdArgs.read(strArgs));

        args.put("jetty.resourcebase", args.get("resourcebase", "src/main/webapp"));
        args.put("datareader.file", args.get("datasource", ""));
        args.put("graph.location", args.get("graph.location", "./graph-cache"));
        System.out.println(args.toString());
        new DemoServer(args).start();
    }
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CmdArgs cmdArgs;
    private GHServer server;

    public DemoServer(CmdArgs args) {
        this.cmdArgs = args;
    }

    /**
     * Starts 'pure' GraphHopper server with the specified injector
     */
    public void start(Injector injector) throws Exception {
        server = new GHServer(cmdArgs);
        server.start(injector);
        logger.info("Memory utilization: " + Helper.getMemInfo() + ", " + cmdArgs.get("graph.flagEncoders", ""));
        
        ObjectMapper mapper = new ObjectMapper();
        //.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setDateFormat(new ISO8601DateFormat());
        mapper.registerModule(new JtsModule());
        RoadData roadData = new RoadData();
        
        List < Point > progLangs = new ArrayList < > ();
        progLangs.add(new Point(1,2));
        progLangs.add(new Point(3,4));
        
        RoadEntry roadEntry = new RoadEntry();
        roadEntry.setId("abc");
        roadEntry.setPoints(progLangs);
        roadData.add(roadEntry);
        
        String json = mapper.writeValueAsString(roadData);

        // Print json
        logger.info(json);
    }

    /**
     * Starts GraphHopper server with routing and matrix module features.
     */
    public void start() throws Exception {
        Injector injector = Guice.createInjector(createModule());
        start(injector);
    }

    protected Module createModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                binder().requireExplicitBindings();

                install(new CustomGuiceModule(cmdArgs));
                install(new GraphHopperServletModule(cmdArgs) {

                    @Override
                    protected void configureServlets() {
                        super.configureServlets();

                        filter("/*").through(ErrorFilter.class, params);
                        bind(ErrorFilter.class).in(Singleton.class);

                        serve("/datafeed*").with(DataFeedServlet.class);
                        bind(DataFeedServlet.class).in(Singleton.class);

                        serve("/roads*").with(RoadsServlet.class);
                        bind(RoadsServlet.class).in(Singleton.class);
                    }
                });

                bind(GuiceFilter.class);
            }
        };
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
