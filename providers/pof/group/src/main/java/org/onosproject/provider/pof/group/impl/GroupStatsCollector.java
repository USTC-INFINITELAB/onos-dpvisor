package org.onosproject.provider.pof.group.impl;


//import org.onlab.util.Timer;
import org.onosproject.pof.controller.PofSwitch;

import org.slf4j.Logger;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;



import static org.slf4j.LoggerFactory.getLogger;

/*
 * Sends Group Stats Request and collect the group statistics with a time interval.
 */
public class GroupStatsCollector implements TimerTask {

    private final PofSwitch sw;
    private final Logger log = getLogger(getClass());
    private final int refreshInterval;

    private Timeout timeout;

    private boolean stopTimer = false;

    /**
     * Creates a GroupStatsCollector object.
     *
     * @param sw Pof switch
     * @param interval time interval for collecting group statistic
     */
    public GroupStatsCollector(PofSwitch sw, int interval) {
        this.sw = sw;
        this.refreshInterval = interval;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        //TODO but ignore now

        return;


    }

    private void sendGroupStatisticRequest() {
        //TODO but ignore now
        return;

    }

    /**
     * Starts the collector.
     */
    public void start() {
        //TODO but ignore now
        return;

    }

    /**
     * Stops the collector.
     */
    public void stop() {
        //TODO but ignore now
        return;

    }
}

