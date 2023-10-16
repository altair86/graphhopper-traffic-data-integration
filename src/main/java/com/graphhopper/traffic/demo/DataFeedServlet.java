package com.graphhopper.traffic.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.graphhopper.http.GraphHopperServlet;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Peter Karich
 */
public class DataFeedServlet extends GraphHopperServlet {

    @Inject
    private ObjectMapper mapper;

    @Inject
    private DataUpdater updater;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        RoadData data = mapper.readValue(req.getInputStream(), RoadData.class);
        System.out.println("data:" + data);

        updater.feed(data, true);
    }
}
