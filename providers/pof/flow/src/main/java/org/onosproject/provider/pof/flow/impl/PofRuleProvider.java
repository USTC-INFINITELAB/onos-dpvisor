/*
 * Copyright 2014-present Open Networking Laboratory
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
package org.onosproject.provider.pof.flow.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.floodlightpof.protocol.OFError;
import org.onosproject.floodlightpof.protocol.OFFlowMod;
import org.onosproject.floodlightpof.protocol.OFMessage;
import org.onosproject.floodlightpof.protocol.OFPortStatus;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.CompletedBatchOperation;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleBatchEntry;
import org.onosproject.net.flow.FlowRuleBatchOperation;
import org.onosproject.net.flow.FlowRuleExtPayLoad;
import org.onosproject.net.flow.FlowRuleProvider;
import org.onosproject.net.flow.FlowRuleProviderRegistry;
import org.onosproject.net.flow.FlowRuleProviderService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.table.FlowTableStore;
import org.onosproject.pof.controller.Dpid;
import org.onosproject.pof.controller.PofController;
import org.onosproject.pof.controller.PofEventListener;
import org.onosproject.pof.controller.PofSwitch;
import org.onosproject.pof.controller.PofSwitchListener;
import org.onosproject.pof.controller.RoleState;
import org.onosproject.pof.controller.ThirdPartyMessage;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.slf4j.LoggerFactory.getLogger;


/**
 * Provider which uses an OpenFlow controller to detect network end-station
 * hosts.
 */
@Component(immediate = true)
public class PofRuleProvider extends AbstractProvider
        implements FlowRuleProvider {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleProviderRegistry providerRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PofController controller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DriverService driverService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore flowTableStore;

    private static final int DEFAULT_POLL_FREQUENCY = 5;
    @Property(name = "flowPollFrequency", intValue = DEFAULT_POLL_FREQUENCY,
            label = "Frequency (in seconds) for polling flow statistics")
    private int flowPollFrequency = DEFAULT_POLL_FREQUENCY;

    private static final boolean DEFAULT_ADAPTIVE_FLOW_SAMPLING = false;
    @Property(name = "adaptiveFlowSampling", boolValue = DEFAULT_ADAPTIVE_FLOW_SAMPLING,
            label = "Adaptive Flow Sampling is on or off")
    private boolean adaptiveFlowSampling = DEFAULT_ADAPTIVE_FLOW_SAMPLING;

    private FlowRuleProviderService providerService;

    private final InternalFlowProvider listener = new InternalFlowProvider();

    private Cache<Long, InternalCacheEntry> pendingBatches;


    /**
     * Creates an OpenFlow host provider.
     */
    public PofRuleProvider() {
        super(new ProviderId("pof", "org.onosproject.provider.pof.flow"));
    }

    @Activate
    public void activate(ComponentContext context) {

        cfgService.registerProperties(getClass());
        providerService = providerRegistry.register(this);
        controller.addListener(listener);
        controller.addEventListener(listener);

        modified(context);

        pendingBatches = createBatchCache();

        //createCollectors();


    }

    @Deactivate
    public void deactivate(ComponentContext context) {
        cfgService.unregisterProperties(getClass(), false);
        //stopCollectors();
        providerRegistry.unregister(this);
        providerService = null;

        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();
        int newFlowPollFrequency;
        try {
            String s = get(properties, "flowPollFrequency");
            newFlowPollFrequency = isNullOrEmpty(s) ? flowPollFrequency : Integer.parseInt(s.trim());

        } catch (NumberFormatException | ClassCastException e) {
            newFlowPollFrequency = flowPollFrequency;
        }

        if (newFlowPollFrequency != flowPollFrequency) {
            flowPollFrequency = newFlowPollFrequency;
            //adjustRate();
        }

        log.info("Settings: flowPollFrequency={}", flowPollFrequency);

        boolean newAdaptiveFlowSampling;
        String s = get(properties, "adaptiveFlowSampling");
        newAdaptiveFlowSampling = isNullOrEmpty(s) ? adaptiveFlowSampling : Boolean.parseBoolean(s.trim());

        if (newAdaptiveFlowSampling != adaptiveFlowSampling) {
            // stop previous collector
            //stopCollectors();
            adaptiveFlowSampling = newAdaptiveFlowSampling;
            // create new collectors
            //createCollectors();
        }

        log.info("Settings: adaptiveFlowSampling={}", adaptiveFlowSampling);
    }

    private Cache<Long, InternalCacheEntry> createBatchCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .removalListener((RemovalNotification<Long, InternalCacheEntry> notification) -> {
                    /*if (notification.getCause() == RemovalCause.EXPIRED) {
                        providerService.batchOperationCompleted(notification.getKey(),
                                notification.getValue().failedCompletion());
                    }*/
                }).build();
    }

//    private void createCollectors() {
//        controller.getSwitches().forEach(this::createCollector);
//    }

    /*wenjian
    private void createCollector(PofSwitch sw) {
        if (adaptiveFlowSampling) {
            // NewAdaptiveFlowStatsCollector Constructor
            NewAdaptiveFlowStatsCollector fsc =
                    new NewAdaptiveFlowStatsCollector(driverService, sw, flowPollFrequency);
            fsc.start();
            afsCollectors.put(new Dpid(sw.getId()), fsc);
        } else {
            FlowStatsCollector fsc = new FlowStatsCollector(timer, sw, flowPollFrequency);
            fsc.start();
            simpleCollectors.put(new Dpid(sw.getId()), fsc);
        }
        TableStatisticsCollector tsc = new TableStatisticsCollector(timer, sw, flowPollFrequency);
        tsc.start();
        tableStatsCollectors.put(new Dpid(sw.getId()), tsc);
    }

    private void stopCollectors() {
        if (adaptiveFlowSampling) {
            // NewAdaptiveFlowStatsCollector Destructor
            afsCollectors.values().forEach(NewAdaptiveFlowStatsCollector::stop);
            afsCollectors.clear();
        } else {
            simpleCollectors.values().forEach(FlowStatsCollector::stop);
            simpleCollectors.clear();
        }
        tableStatsCollectors.values().forEach(TableStatisticsCollector::stop);
        tableStatsCollectors.clear();
    }
    */

    /*wenjian
    private void adjustRate() {
        DefaultLoad.setPollInterval(flowPollFrequency);
        if (adaptiveFlowSampling) {
            // NewAdaptiveFlowStatsCollector calAndPollInterval
            afsCollectors.values().forEach(fsc -> fsc.adjustCalAndPollInterval(flowPollFrequency));
        } else {
            simpleCollectors.values().forEach(fsc -> fsc.adjustPollInterval(flowPollFrequency));
        }
        tableStatsCollectors.values().forEach(tsc -> tsc.adjustPollInterval(flowPollFrequency));
    }
    */

    @Override
    public void applyFlowRule(FlowRule... flowRules) {
        for (FlowRule flowRule : flowRules) {
            applyRule(flowRule);
        }
    }


    private void applyRule(FlowRule flowRule) {
        Dpid dpid = Dpid.dpid(flowRule.deviceId().uri());
        PofSwitch sw = controller.getSwitch(dpid);
        log.info("++++ sqy applyRule");
        if (sw == null) {
            return;
        }

        FlowRuleExtPayLoad flowRuleExtPayLoad = flowRule.payLoad();
        if (hasPayload(flowRuleExtPayLoad)) {
            OFMessage msg = new ThirdPartyMessage(flowRuleExtPayLoad.payLoad());
            sw.sendMsg(msg);
            return;
        }
        log.info("++++ sqy before ofMessage:");
        OFMessage ofMessage = FlowModBuilder.builder(flowRule, sw.factory(), flowTableStore,
                Optional.empty(), Optional.of(driverService)).buildFlowAdd();
        log.info("++++ sqy ofMessage: " + ofMessage.toString());

        sw.sendMsg(ofMessage);

//        DefaultFlowEntry flowEntry = (DefaultFlowEntry) flowRule;
//        flowEntry.setState(FlowEntry.FlowEntryState.ADDED);

        if (adaptiveFlowSampling) {
            // Add TypedFlowEntry to deviceFlowEntries in NewAdaptiveFlowStatsCollector
            //NewAdaptiveFlowStatsCollector collector = afsCollectors.get(dpid);
//            if (collector != null) {
//                collector.addWithFlowRule(flowRule);
//            }
        }
    }

    @Override
    public void removeFlowRule(FlowRule... flowRules) {
        for (FlowRule flowRule : flowRules) {
            log.info("++++ ++++++++++pofruleprovider; {}", flowRule.deviceId().toString());
            removeRule(flowRule);
        }
    }

    private void removeRule(FlowRule flowRule) {
        Dpid dpid = Dpid.dpid(flowRule.deviceId().uri());
        PofSwitch sw = controller.getSwitch(dpid);

        if (sw == null) {
            return;
        }

        log.info("delFlowEntry : {}", flowRule.id().toString());
        FlowRuleExtPayLoad flowRuleExtPayLoad = flowRule.payLoad();
        if (hasPayload(flowRuleExtPayLoad)) {
            OFMessage msg = new ThirdPartyMessage(flowRuleExtPayLoad.payLoad());
            sw.sendMsg(msg);
            return;
        }
        log.info("++++ ++++++++++pofruleprovider; {}");
        sw.sendMsg(FlowModBuilder.builder(flowRule, sw.factory(), flowTableStore,
                Optional.empty(), Optional.of(driverService)).buildFlowDel());

//        DefaultFlowEntry flowEntry = (DefaultFlowEntry) flowRule;
//        flowEntry.setState(FlowEntry.FlowEntryState.REMOVED);

        if (adaptiveFlowSampling) {
            // Remove TypedFlowEntry to deviceFlowEntries in NewAdaptiveFlowStatsCollector
//            NewAdaptiveFlowStatsCollector collector = afsCollectors.get(dpid);
//            if (collector != null) {
//                collector.removeFlows(flowRule);
//            }
        }
    }

    @Override
    public void removeRulesById(ApplicationId id, FlowRule... flowRules) {
        // TODO: optimize using the ApplicationId
        removeFlowRule(flowRules);
    }

    @Override
    public void executeBatch(FlowRuleBatchOperation batch) {
        checkNotNull(batch);
        //log.info("PofFlowRuleProvider executeBatch()");
        pendingBatches.put(batch.id(), new InternalCacheEntry(batch));


        Dpid dpid = Dpid.dpid(batch.deviceId().uri());

        PofSwitch sw = controller.getSwitch(dpid);
        OFFlowMod mod;

        for (FlowRuleBatchEntry fbe : batch.getOperations()) {
            // flow is the third party privacy flow

            FlowRuleExtPayLoad flowRuleExtPayLoad = fbe.target().payLoad();

            if (hasPayload(flowRuleExtPayLoad)) {
                log.info("+++++ PofFlowRuleProvider hasPayload(flowRuleExtPayLoad)?");
                OFMessage msg = new ThirdPartyMessage(flowRuleExtPayLoad.payLoad());
                sw.sendMsg(msg);
                continue;
            }
            //log.info("+++++ before flowModBuilder");
            FlowModBuilder builder =
                    FlowModBuilder.builder(fbe.target(), sw.factory(), flowTableStore,
                            Optional.empty(), Optional.of(driverService));

            //log.info("+++++ PofFlowRuleProvider fbe.operator() :{}", fbe.operator());
            switch (fbe.operator()) {
                case ADD:
                    mod = builder.buildFlowAdd();

//                    flowEntry.setState(FlowEntry.FlowEntryState.ADDED);

                    break;
                case REMOVE:
                    mod = builder.buildFlowDel();
//                    flowEntry.setState(FlowEntry.FlowEntryState.REMOVED);
                    break;
                case MODIFY:
                    mod = builder.buildFlowMod();
//                    flowEntry.setState(FlowEntry.FlowEntryState.ADDED);
                    break;
                default:
                    log.error("Unsupported batch operation {}; skipping flowmod {}",
                            fbe.operator(), fbe);
                    continue;
            }
            sw.sendMsg(mod);
        }

        //In pof, no BARRIER_REPLY yet
        CompletedBatchOperation op =
                new CompletedBatchOperation(true, Collections.emptySet(),
                                            batch.deviceId());
        providerService.batchOperationCompleted(batch.id(), op);

    }

    private boolean hasPayload(FlowRuleExtPayLoad flowRuleExtPayLoad) {
        return flowRuleExtPayLoad != null &&
                flowRuleExtPayLoad.payLoad() != null &&
                flowRuleExtPayLoad.payLoad().length > 0;
    }

    private class InternalFlowProvider
            implements PofSwitchListener, PofEventListener {

        @Override
        public void switchAdded(Dpid dpid) {

            PofSwitch sw = controller.getSwitch(dpid);

            //createCollector(controller.getSwitch(dpid));
        }

        @Override
        public void handleConnectionUp(Dpid dpid){

        }

        @Override
        public void switchRemoved(Dpid dpid) {
//            if (adaptiveFlowSampling) {
//                NewAdaptiveFlowStatsCollector collector = afsCollectors.remove(dpid);
//                if (collector != null) {
//                    collector.stop();
//                }
//            } else {
//                FlowStatsCollector collector = simpleCollectors.remove(dpid);
//                if (collector != null) {
//                    collector.stop();
//                }
//            }
//            TableStatisticsCollector tsc = tableStatsCollectors.remove(dpid);
//            if (tsc != null) {
//                tsc.stop();
//            }
        }

        @Override
        public void switchChanged(Dpid dpid) {
        }

        @Override
        public void portChanged(Dpid dpid, OFPortStatus status) {
            // TODO: Decide whether to evict flows internal store.
        }

        @Override
        public void setTableResource(Dpid dpid, OFFlowTableResource msg) {
            // TODO: Decide whether to evict flows internal store.
        }

        @Override
        public void handleMessage(Dpid dpid, OFMessage msg) {
            if (providerService == null) {
                // We are shutting down, nothing to be done
                return;
            }
            DeviceId deviceId = DeviceId.deviceId(Dpid.uri(dpid));
            switch (msg.getType()) {
                /*wenjian
                case FLOW_REMOVED:
                    OFFlowRemoved removed = (OFFlowRemoved) msg;

                    FlowEntry fr = new FlowEntryBuilder(deviceId, removed, driverService).build();
                    providerService.flowRemoved(fr);

                    if (adaptiveFlowSampling) {
                        // Removed TypedFlowEntry to deviceFlowEntries in NewAdaptiveFlowStatsCollector
                        NewAdaptiveFlowStatsCollector collector = afsCollectors.get(dpid);
                        if (collector != null) {
                            collector.flowRemoved(fr);
                        }
                    }
                    break;
                    wenjian*/
                case MULTIPART_REPLY:
                    /*wenjian, i am sorry that pof is not this reply yet
                    if (((OFFlowStatisticsReply) msg).getStatsType() == OFStatisticsType.FLOW) {
                        pushFlowMetrics(dpid, (OFFlowStatsReply) msg);
                    } else if (((OFStatsReply) msg).getStatsType() == OFStatsType.TABLE) {
                        pushTableStatistics(dpid, (OFTableStatsReply) msg);
                    }
                    */
                    break;
                /*wenjian
                case BARRIER_REPLY:
                    try {
                        InternalCacheEntry entry = pendingBatches.getIfPresent(msg.getXid());
                        if (entry != null) {
                            providerService
                                    .batchOperationCompleted(msg.getXid(),
                                                             entry.completed());
                        } else {
                            log.warn("Received unknown Barrier Reply: {}",
                                     msg.getXid());
                        }
                    } finally {
                        pendingBatches.invalidate(msg.getXid());
                    }
                    break;
                    wenjian*/
                case ERROR:
                    /*wenjian
                    // TODO: This needs to get suppressed in a better way.
                    if (msg instanceof OFBadRequestErrorMsg &&
                            ((OFBadRequestErrorMsg) msg).getCode() == OFBadRequestCode.BAD_TYPE) {
                        log.debug("Received error message {} from {}", msg, dpid);
                    } else {
                        log.warn("Received error message {} from {}", msg, dpid);
                    }
                    wenjian*/
                    OFError error = (OFError) msg;
                    if (error.getErrorType() == OFError.OFErrorType.OFPET_FLOW_MOD_FAILED.getValue()) {
                        /*wenjian

                        OFFlowModFailedErrorMsg fmFailed = (OFFlowModFailedErrorMsg) error;
                        if (fmFailed.getData().getParsedMessage().isPresent()) {
                            OFMessage m = fmFailed.getData().getParsedMessage().get();
                            OFFlowMod fm = (OFFlowMod) m;
                            InternalCacheEntry entry =
                                    pendingBatches.getIfPresent(msg.getXid());
                            if (entry != null) {
                                entry.appendFailure(new FlowEntryBuilder(deviceId, fm, driverService).build());
                            } else {
                                log.error("No matching batch for this error: {}", error);
                            }
                        } else {
                            // FIXME: Potentially add flowtracking to avoid this message.
                            log.error("Flow installation failed but switch didn't"
                                              + " tell us which one.");
                        }
                        */
                        log.error("FLOW_MOD_FAILED");
                    }
                    break;
                default:
                    log.debug("Unhandled message type: {}", msg.getType());
            }
        }

        @Override
        public void receivedRoleReply(Dpid dpid, RoleState requested,
                                      RoleState response) {
            // Do nothing here for now.
        }

        /*wenjian
        private void pushFlowMetrics(Dpid dpid, OFFlowStatsReply replies) {

            DeviceId did = DeviceId.deviceId(Dpid.uri(dpid));

            List<FlowEntry> flowEntries = replies.getEntries().stream()
                    .map(entry -> new FlowEntryBuilder(did, entry, driverService).build())
                    .collect(Collectors.toList());

            if (adaptiveFlowSampling)  {
                NewAdaptiveFlowStatsCollector afsc = afsCollectors.get(dpid);

                synchronized (afsc) {
                    if (afsc.getFlowMissingXid() != NewAdaptiveFlowStatsCollector.NO_FLOW_MISSING_XID) {
                        log.debug("OpenFlowRuleProvider:pushFlowMetrics, flowMissingXid={}, "
                                        + "OFFlowStatsReply Xid={}, for {}",
                                afsc.getFlowMissingXid(), replies.getXid(), dpid);
                    }

                    // Check that OFFlowStatsReply Xid is same with the one of OFFlowStatsRequest?
                    if (afsc.getFlowMissingXid() != NewAdaptiveFlowStatsCollector.NO_FLOW_MISSING_XID) {
                        if (afsc.getFlowMissingXid() == replies.getXid()) {
                            // call entire flow stats update with flowMissing synchronization.
                            // used existing pushFlowMetrics
                            providerService.pushFlowMetrics(did, flowEntries);
                        }
                        // reset flowMissingXid to NO_FLOW_MISSING_XID
                        afsc.setFlowMissingXid(NewAdaptiveFlowStatsCollector.NO_FLOW_MISSING_XID);

                    } else {
                        // call individual flow stats update
                        providerService.pushFlowMetricsWithoutFlowMissing(did, flowEntries);
                    }

                    // Update TypedFlowEntry to deviceFlowEntries in NewAdaptiveFlowStatsCollector
                    afsc.pushFlowMetrics(flowEntries);
                }
            } else {
                // call existing entire flow stats update with flowMissing synchronization
                providerService.pushFlowMetrics(did, flowEntries);
            }
        }

        private void pushTableStatistics(Dpid dpid, OFTableStatsReply replies) {

            DeviceId did = DeviceId.deviceId(Dpid.uri(dpid));
            List<TableStatisticsEntry> tableStatsEntries = replies.getEntries().stream()
                    .map(entry -> buildTableStatistics(did, entry))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            providerService.pushTableStatistics(did, tableStatsEntries);
        }

        private TableStatisticsEntry buildTableStatistics(DeviceId deviceId,
                                                          OFTableStatsEntry ofEntry) {
            TableStatisticsEntry entry = null;
            if (ofEntry != null) {
                entry = new DefaultTableStatisticsEntry(deviceId,
                                                        ofEntry.getTableId().getValue(),
                                                        ofEntry.getActiveCount(),
                                                        ofEntry.getLookupCount().getValue(),
                                                        ofEntry.getMatchedCount().getValue());
            }

            return entry;

        }
        wenjian*/
    }

    /**
     * The internal cache entry holding the original request as well as
     * accumulating the any failures along the way.
     * <p/>
     * If this entry is evicted from the cache then the entire operation is
     * considered failed. Otherwise, only the failures reported by the device
     * will be propagated up.
     */
    private class InternalCacheEntry {

        private final FlowRuleBatchOperation operation;
        private final Set<FlowRule> failures = Sets.newConcurrentHashSet();

        public InternalCacheEntry(FlowRuleBatchOperation operation) {
            this.operation = operation;
        }

        /**
         * Appends a failed rule to the set of failed items.
         *
         * @param rule the failed rule
         */
        public void appendFailure(FlowRule rule) {
            failures.add(rule);
        }

        /**
         * Fails the entire batch and returns the failed operation.
         *
         * @return the failed operation
         */
        public CompletedBatchOperation failedCompletion() {
            Set<FlowRule> fails = operation.getOperations().stream()
                    .map(op -> op.target()).collect(Collectors.toSet());
            return new CompletedBatchOperation(false,
                    Collections
                            .unmodifiableSet(fails),
                    operation.deviceId());
        }

        /**
         * Returns the completed operation and whether the batch suceeded.
         *
         * @return the completed operation
         */
        public CompletedBatchOperation completed() {
            return new CompletedBatchOperation(
                    failures.isEmpty(),
                    Collections
                            .unmodifiableSet(failures),
                    operation.deviceId());
        }
    }

}
