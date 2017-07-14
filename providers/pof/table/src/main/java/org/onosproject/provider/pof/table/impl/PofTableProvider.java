/**
 * edited by hdy
 * */
package org.onosproject.provider.pof.table.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Maps;
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
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.OFMessage;
import org.onosproject.floodlightpof.protocol.OFPortStatus;
import org.onosproject.floodlightpof.protocol.OFType;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.floodlightpof.protocol.table.OFTableMod;
import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.table.CompletedTableBatchOperation;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableBatchEntry;
import org.onosproject.net.table.FlowTableBatchOperation;
import org.onosproject.net.table.FlowTableExtPayLoad;
import org.onosproject.net.table.FlowTableProvider;
import org.onosproject.net.table.FlowTableProviderRegistry;
import org.onosproject.net.table.FlowTableProviderService;
import org.onosproject.net.table.FlowTableStore;
import org.onosproject.pof.controller.Dpid;
import org.onosproject.pof.controller.PofController;
import org.onosproject.pof.controller.PofEventListener;
import org.onosproject.pof.controller.PofSwitch;
import org.onosproject.pof.controller.PofSwitchListener;
import org.onosproject.pof.controller.RoleState;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
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
public class PofTableProvider extends AbstractProvider
        implements FlowTableProvider {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableProviderRegistry providerRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PofController controller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DriverService driverService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore tableStore;

    private static final int DEFAULT_POLL_FREQUENCY = 5;
    @Property(name = "flowPollFrequency", intValue = DEFAULT_POLL_FREQUENCY,
            label = "Frequency (in seconds) for polling flow statistics")
    private int flowPollFrequency = DEFAULT_POLL_FREQUENCY;

    private static final boolean DEFAULT_ADAPTIVE_FLOW_SAMPLING = false;
    @Property(name = "adaptiveTableSampling", boolValue = DEFAULT_ADAPTIVE_FLOW_SAMPLING,
            label = "Adaptive Flow Sampling is on or off")
    private boolean adaptiveTableSampling = DEFAULT_ADAPTIVE_FLOW_SAMPLING;

    private FlowTableProviderService providerService;

    private final InternalFlowProvider listener = new InternalFlowProvider();

    private Cache<Long, InternalCacheEntry> pendingBatches;

    private final Timer timer = new Timer("onos-pof-collector");
    private final Map<Dpid, TableStatsCollector> simpleCollectors = Maps.newHashMap();

//    NewAdaptiveFlowStatsCollector Set
//    private final Map<Dpid, NewAdaptiveFlowStatsCollector> afsCollectors = Maps.newHashMap();
//    private final Map<Dpid, TableStatsCollector> collectors = Maps.newHashMap();

    /**
     * Creates an pof table provider.
     */
    public PofTableProvider() {
        super(new ProviderId("pof", "org.onosproject.provider.pof.table"));
    }

    @Activate
    public void activate(ComponentContext context) {
        cfgService.registerProperties(getClass());
        providerService = providerRegistry.register(this);
        controller.addListener(listener);
        controller.addEventListener(listener);

        modified(context);

        pendingBatches = createBatchCache();

//        createCollectors();

        log.info("Started with flowPollFrequency = {}, adaptiveTableSampling = {}",
                flowPollFrequency, adaptiveTableSampling);
    }

    @Deactivate
    public void deactivate(ComponentContext context) {
        cfgService.unregisterProperties(getClass(), false);
//        stopCollectors();
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
//            adjustRate();
        }

        log.info("Settings: flowPollFrequency={}", flowPollFrequency);

        boolean newadaptiveTableSampling;
        String s = get(properties, "adaptiveTableSampling");
        newadaptiveTableSampling = isNullOrEmpty(s) ? adaptiveTableSampling : Boolean.parseBoolean(s.trim());

        if (newadaptiveTableSampling != adaptiveTableSampling) {
            // stop previous collector
//            stopCollectors();
            adaptiveTableSampling = newadaptiveTableSampling;
            // create new collectors
//            createCollectors();
        }

        log.info("Settings: adaptiveTableSampling={}", adaptiveTableSampling);
    }

    private Cache<Long, InternalCacheEntry> createBatchCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .removalListener((RemovalNotification<Long, InternalCacheEntry> notification) -> {
                    if (notification.getCause() == RemovalCause.EXPIRED) {
                        providerService.batchOperationCompleted(notification.getKey(),
                                                                notification.getValue().failedCompletion());
                    }
                }).build();
    }

//    private void createCollectors() {
//        controller.getSwitches().forEach(this::createCollector);
//    }
//
//    private void createCollector(PofSwitch sw) {
//        if (adaptiveTableSampling) {
//            // NewAdaptiveFlowStatsCollector Constructor
//            NewAdaptiveFlowStatsCollector fsc =
//                    new NewAdaptiveFlowStatsCollector(driverService, sw, flowPollFrequency);
//            fsc.start();
//            afsCollectors.put(new Dpid(sw.getId()), fsc);
//        } else {
//            TableStatsCollector fsc = new TableStatsCollector(timer, sw, flowPollFrequency);
//            fsc.start();
//            simpleCollectors.put(new Dpid(sw.getId()), fsc);
//        }
////        TableStatisticsCollector tsc = new TableStatisticsCollector(timer, sw, flowPollFrequency);
////        tsc.start();
////        tableStatsCollectors.put(new Dpid(sw.getId()), tsc);
//    }

//    private void stopCollectors() {
//        if (adaptiveTableSampling) {
//            // NewAdaptiveFlowStatsCollector Destructor
//            afsCollectors.values().forEach(NewAdaptiveFlowStatsCollector::stop);
//            afsCollectors.clear();
//        } else {
//            simpleCollectors.values().forEach(TableStatsCollector::stop);
//            simpleCollectors.clear();
//        }
//        tableStatsCollectors.values().forEach(TableStatisticsCollector::stop);
//        tableStatsCollectors.clear();
//    }

//    private void adjustRate() {
//        DefaultLoad.setPollInterval(flowPollFrequency);
//        if (adaptiveTableSampling) {
//            // NewAdaptiveFlowStatsCollector calAndPollInterval
//            afsCollectors.values().forEach(fsc -> fsc.adjustCalAndPollInterval(flowPollFrequency));
//        } else {
//            simpleCollectors.values().forEach(fsc -> fsc.adjustPollInterval(flowPollFrequency));
//        }
//        tableStatsCollectors.values().forEach(tsc -> tsc.adjustPollInterval(flowPollFrequency));
//    }
    //added by hdy
    private OFFlowTable buildOFFlowTable(FlowTable flowTable) {

        OFFlowTable ofFlowTable = flowTable.flowTable();

        //parse global ID to small ID
        byte globalTableId = (byte) flowTable.id().value();
        DeviceId deviceId = flowTable.deviceId();
        byte smallTableId = tableStore.parseToSmallTableId(deviceId, globalTableId);
        ofFlowTable.setTableId(smallTableId);

        //calculate keyLength and fild numbers
        List<OFMatch20> matchList = ofFlowTable.getMatchFieldList();
        byte fieldNum = (byte) matchList.size();
        short keyLength = 0;
        for (OFMatch20 field : matchList) {
            keyLength += field.getLength();
        }

        if (fieldNum == 0 || keyLength == 0) {
            log.error("++++ build an error OFFlowTable! " + ofFlowTable.toString());
        }
        ofFlowTable.setKeyLength(keyLength);
        ofFlowTable.setMatchFieldNum(fieldNum);
        return ofFlowTable;
    }

    @Override
    public void applyFlowTable(FlowTable... flowTables) {

        for (FlowTable flowTable : flowTables) {
            OFFlowTable ofFlowTable = buildOFFlowTable(flowTable);
            DeviceId deviceId = flowTable.deviceId();
            applyTable(deviceId, ofFlowTable);
        }
    }

    @Override
    public void applyTable(DeviceId deviceid, OFFlowTable flowTable) {
        log.info("+++++ pof table provider. applyTable");
        Dpid dpid = Dpid.dpid(deviceid.uri());
        PofSwitch sw = controller.getSwitch(dpid);

        if (sw == null) {
//            log.info("+++++ pofswitch is null");
            return;
        }
        log.info("+++++ pof table provider. applyTable");
        OFTableMod tablemod = (OFTableMod) sw.factory().getOFMessage(OFType.TABLE_MOD);
        tablemod.setFlowTable(flowTable);
        tablemod.setType(OFType.TABLE_MOD);
        sw.sendMsg(tablemod);
    }

    @Override
    public void removeFlowTable(FlowTable... flowTables) {
        for (FlowTable flowTable : flowTables) {
            removeTable(flowTable);
        }
    }

    private void removeTable(FlowTable flowTable) {
        Dpid dpid = Dpid.dpid(flowTable.deviceId().uri());
        PofSwitch sw = controller.getSwitch(dpid);

        if (sw == null) {
            return;
        }

        byte globalTableId = (byte) flowTable.id().value();
        DeviceId deviceId = flowTable.deviceId();
        byte smallTableId = tableStore.parseToSmallTableId(deviceId, globalTableId);
        flowTable.flowTable().setTableId(smallTableId);

        OFTableMod tablemod = (OFTableMod) sw.factory().getOFMessage(OFType.TABLE_MOD);
        tablemod.setFlowTable(flowTable.flowTable());
        sw.sendMsg(tablemod);

    }

    @Override
    public void removeTablesById(ApplicationId id, FlowTable... flowTables) {
        // TODO: optimize using the ApplicationId
        removeFlowTable(flowTables);
    }

    @Override
    public void executeBatch(FlowTableBatchOperation batch) {
        checkNotNull(batch);

        pendingBatches.put(batch.id(), new InternalCacheEntry(batch));

        Dpid dpid = Dpid.dpid(batch.deviceId().uri());
        PofSwitch sw = controller.getSwitch(dpid);
        OFTableMod tablemod = (OFTableMod) sw.factory().getOFMessage(OFType.TABLE_MOD);

        for (FlowTableBatchEntry fbe : batch.getOperations()) {
            // flow is the third party privacy flow

            OFFlowTable flowTable = buildOFFlowTable(fbe.target());

            switch (fbe.operator()) {
                case ADD:
                    flowTable.setCommand(OFTableMod.OFTableModCmd.OFPTC_ADD);
                    break;
                case MODIFY:
                    flowTable.setCommand(OFTableMod.OFTableModCmd.OFPTC_MODIFY);
                    break;
                case REMOVE:

                    flowTable.setCommand(OFTableMod.OFTableModCmd.OFPTC_DELETE);
                    break;
                default:
                    log.error("Unsupported batch operation {}; skipping flowmod {}",
                            fbe.operator(), fbe);
                    continue;
            }
            tablemod.setFlowTable(flowTable);
            tablemod.setType(OFType.TABLE_MOD);
            sw.sendMsg(tablemod);
        }
//        OFBarrierRequest.Builder builder = sw.factory().buildBarrierRequest()
//                .setXid(batch.id());
//        sw.sendMsg(builder.build());
    }

    private boolean hasPayload(FlowTableExtPayLoad flowTableExtPayLoad) {
        return flowTableExtPayLoad != null &&
                flowTableExtPayLoad.payLoad() != null &&
                flowTableExtPayLoad.payLoad().length > 0;
    }

    private class InternalFlowProvider
            implements PofSwitchListener, PofEventListener {

        @Override
        public void switchAdded(Dpid dpid) {

            PofSwitch sw = controller.getSwitch(dpid);

//            createCollector(controller.getSwitch(dpid));
        }

        @Override
        public void handleConnectionUp(Dpid dpid){

        }

        @Override
        public void switchRemoved(Dpid dpid) {
            if (adaptiveTableSampling) {
//                NewAdaptiveFlowStatsCollector collector = afsCollectors.remove(dpid);
//                if (collector != null) {
//                    collector.stop();
//                }
            } else {
//                FlowStatsCollector collector = simpleCollectors.remove(dpid);
//                if (collector != null) {
//                    collector.stop();
//                }
            }
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
        public void setTableResource(Dpid dpid, OFFlowTableResource status){

        }

        @Override
        public void handleMessage(Dpid dpid, OFMessage msg) {
            if (providerService == null) {
                // We are shutting down, nothing to be done
                return;
            }
            DeviceId deviceId = DeviceId.deviceId(Dpid.uri(dpid));
/*            switch (msg.getType()) {
                case FLOW_REMOVED:
                    OFFlowRemoved removed = (OFFlowRemoved) msg;

                    FlowEntry fr = new FlowEntryBuilder(deviceId, removed, driverService).build();
                    providerService.flowRemoved(fr);

                    if (adaptiveTableSampling) {
                        // Removed TypedFlowEntry to deviceFlowEntries in NewAdaptiveFlowStatsCollector
                        NewAdaptiveFlowStatsCollector collector = afsCollectors.get(dpid);
                        if (collector != null) {
                            collector.flowRemoved(fr);
                        }
                    }
                    break;
                case STATS_REPLY:
                    if (((OFStatsReply) msg).getStatsType() == OFStatsType.FLOW) {
                        pushFlowMetrics(dpid, (OFFlowStatsReply) msg);
                    } else if (((OFStatsReply) msg).getStatsType() == OFStatsType.TABLE) {
                        pushTableStatistics(dpid, (OFTableStatsReply) msg);
                    }
                    break;
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
                case ERROR:
                    // TODO: This needs to get suppressed in a better way.
                    if (msg instanceof OFBadRequestErrorMsg &&
                            ((OFBadRequestErrorMsg) msg).getCode() == OFBadRequestCode.BAD_TYPE) {
                        log.debug("Received error message {} from {}", msg, dpid);
                    } else {
                        log.warn("Received error message {} from {}", msg, dpid);
                    }

                    OFErrorMsg error = (OFErrorMsg) msg;
                    if (error.getErrType() == OFErrorType.FLOW_MOD_FAILED) {
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
                    }
                    break;
                default:
                    log.debug("Unhandled message type: {}", msg.getType());
            }*/
        }

        @Override
        public void receivedRoleReply(Dpid dpid, RoleState requested,
                                      RoleState response) {
            // Do nothing here for now.
        }

/*        private void pushFlowMetrics(Dpid dpid, OFFlowStatsReply replies) {

            DeviceId did = DeviceId.deviceId(Dpid.uri(dpid));

            List<FlowTable> flowTables = replies.getEntries().stream()
                    .map(entry -> new FlowEntryBuilder(did, entry, driverService).build())
                    .collect(Collectors.toList());

            if (adaptiveTableSampling)  {
                NewAdaptiveFlowStatsCollector afsc = afsCollectors.get(dpid);

                synchronized (afsc) {
                    if (afsc.getFlowMissingXid() != NewAdaptiveFlowStatsCollector.NO_FLOW_MISSING_XID) {
                        log.debug("OpenFlowTableProvider:pushFlowMetrics, flowMissingXid={}, "
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
        }*/

/*        private void pushTableStatistics(Dpid dpid, OFTableStatsReply replies) {

            DeviceId did = DeviceId.deviceId(Dpid.uri(dpid));
            List<TableStatisticsEntry> tableStatsEntries = replies.getEntries().stream()
                    .map(entry -> buildTableStatistics(did, entry))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            providerService.pushTableStatistics(did, tableStatsEntries);
        }*/

/*        private TableStatisticsEntry buildTableStatistics(DeviceId deviceId,
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

        }*/
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

        private final FlowTableBatchOperation operation;
        private final Set<FlowTable> failures = Sets.newConcurrentHashSet();

        public InternalCacheEntry(FlowTableBatchOperation operation) {
            this.operation = operation;
        }

        /**
         * Appends a failed rule to the set of failed items.
         *
         * @param table the failed rule
         */
        public void appendFailure(FlowTable table) {
            failures.add(table);
        }

        /**
         * Fails the entire batch and returns the failed operation.
         *
         * @return the failed operation
         */
        public CompletedTableBatchOperation failedCompletion() {
            Set<FlowTable> fails = operation.getOperations().stream()
                    .map(op -> op.target()).collect(Collectors.toSet());
            return new CompletedTableBatchOperation(false,
                                               Collections
                                                       .unmodifiableSet(fails),
                                               operation.deviceId());
        }

        /**
         * Returns the completed operation and whether the batch suceeded.
         *
         * @return the completed operation
         */
        public CompletedTableBatchOperation completed() {
            return new CompletedTableBatchOperation(
                    failures.isEmpty(),
                    Collections
                            .unmodifiableSet(failures),
                    operation.deviceId());
        }
    }

}
