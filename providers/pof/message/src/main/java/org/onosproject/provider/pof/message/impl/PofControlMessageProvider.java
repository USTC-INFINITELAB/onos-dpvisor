/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.provider.pof.message.impl;

import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.metrics.MetricsService;
import org.onlab.util.SharedScheduledExecutorService;
import org.onlab.util.SharedScheduledExecutors;
import org.onosproject.cpman.message.ControlMessageProvider;
import org.onosproject.cpman.message.ControlMessageProviderRegistry;
import org.onosproject.cpman.message.ControlMessageProviderService;
import org.onosproject.floodlightpof.protocol.OFMessage;
import org.onosproject.floodlightpof.protocol.OFPortStatus;
import org.onosproject.floodlightpof.protocol.OFType;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.net.DeviceId;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.pof.controller.Dpid;
import org.onosproject.pof.controller.PofController;
import org.onosproject.pof.controller.PofEventListener;
import org.onosproject.pof.controller.PofSwitch;
import org.onosproject.pof.controller.PofSwitchListener;
import org.onosproject.pof.controller.RoleState;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.onosproject.net.DeviceId.deviceId;
import static org.onosproject.pof.controller.Dpid.uri;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provider which uses an POF controller to collect control message.
 */
@Component(immediate = true)
public class PofControlMessageProvider extends AbstractProvider
        implements ControlMessageProvider {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ControlMessageProviderRegistry providerRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PofController controller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MetricsService metricsService;

    private ControlMessageProviderService providerService;

    private final InternalDeviceProvider listener = new InternalDeviceProvider();

    private final InternalIncomingMessageProvider inMsgListener =
                    new InternalIncomingMessageProvider();

    private final InternalOutgoingMessageProvider outMsgListener =
                    new InternalOutgoingMessageProvider();

    private HashMap<Dpid, PofControlMessageAggregator> aggregators = Maps.newHashMap();
    private SharedScheduledExecutorService executor;
    private static final int AGGR_INIT_DELAY = 1;
    private static final int AGGR_PERIOD = 1;
    private static final TimeUnit AGGR_TIME_UNIT = TimeUnit.MINUTES;
    private HashMap<Dpid, ScheduledFuture<?>> executorResults = Maps.newHashMap();

    /**
     * Creates a provider with the supplier identifier.
     */
    public PofControlMessageProvider() {
        super(new ProviderId("pof", "org.onosproject.provider.pof"));
    }

    @Activate
    protected void activate() {
        providerService = providerRegistry.register(this);

        // listens all OpenFlow device related events
        controller.addListener(listener);

        // listens all OpenFlow incoming message events
        controller.addEventListener(inMsgListener);
        controller.monitorAllEvents(true);

        // listens all OpenFlow outgoing message events
        controller.getSwitches().forEach(sw -> sw.addEventListener(outMsgListener));

        executor = SharedScheduledExecutors.getSingleThreadExecutor();

        connectInitialDevices();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        controller.removeListener(listener);
        providerRegistry.unregister(this);
        providerService = null;

        // stops listening all OpenFlow incoming message events
        controller.monitorAllEvents(false);
        controller.removeEventListener(inMsgListener);

        // stops listening all OpenFlow outgoing message events
        controller.getSwitches().forEach(sw -> sw.removeEventListener(outMsgListener));

        log.info("Stopped");
    }

    private void connectInitialDevices() {
        for (PofSwitch sw: controller.getSwitches()) {
            try {
                listener.switchAdded(new Dpid(sw.getId()));
            } catch (Exception e) {
                log.warn("Failed initially adding {} : {}", sw.getStringId(), e.getMessage());
                log.debug("Error details:", e);
            }
        }
    }

    /**
     * A listener for OpenFlow switch event.
     */
    private class InternalDeviceProvider implements PofSwitchListener {

        @Override
        public void switchAdded(Dpid dpid) {
            if (providerService == null) {
                return;
            }

            PofSwitch sw = controller.getSwitch(dpid);
            if (sw != null) {
                // start to monitor the outgoing control messages
                sw.addEventListener(outMsgListener);
            }

            DeviceId deviceId = deviceId(uri(dpid));
            PofControlMessageAggregator ofcma =
                    new PofControlMessageAggregator(metricsService,
                            providerService, deviceId);
            ScheduledFuture result = executor.scheduleAtFixedRate(ofcma,
                    AGGR_INIT_DELAY, AGGR_PERIOD, AGGR_TIME_UNIT, true);
            aggregators.put(dpid, ofcma);
            executorResults.put(dpid, result);
        }

        @Override
        public void switchRemoved(Dpid dpid) {
            if (providerService == null) {
                return;
            }

            PofSwitch sw = controller.getSwitch(dpid);
            if (sw != null) {
                // stop monitoring the outgoing control messages
                sw.removeEventListener(outMsgListener);
            }

            // removes the aggregator when switch is removed
            // this also stops the aggregator from running
            PofControlMessageAggregator aggregator = aggregators.remove(dpid);
            if (aggregator != null) {
                executorResults.get(dpid).cancel(true);
                executorResults.remove(dpid);
            }
        }

        @Override
        public void handleConnectionUp(Dpid dpid){

        }

        @Override
        public void switchChanged(Dpid dpid) {
        }

        @Override
        public void portChanged(Dpid dpid, OFPortStatus status) {
        }

        @Override
        public void setTableResource(Dpid dpid, OFFlowTableResource msg){

        }

        @Override
        public void receivedRoleReply(Dpid dpid, RoleState requested, RoleState response) {
        }
    }

    /**
     * A listener for incoming OpenFlow messages.
     */
    private class InternalIncomingMessageProvider implements PofEventListener {


        //       || msg.getType() == OFType.STATS_REPLY
        @Override
        public void handleMessage(Dpid dpid, OFMessage msg) {
            if (msg.getType() == OFType.PACKET_IN ||
                    msg.getType() == OFType.FLOW_MOD) {
                aggregators.computeIfPresent(dpid, (k, v) -> {
                    v.increment(msg);
                    return v;
                });
            }
        }
    }

    /**
     * A listener for outgoing OpenFlow messages.
     */
    private class InternalOutgoingMessageProvider implements PofEventListener {

        @Override
        public void handleMessage(Dpid dpid, OFMessage msg) {
            aggregators.computeIfPresent(dpid, (k, v) -> {
                v.increment(msg);
                return v;
            });
        }
    }
}
