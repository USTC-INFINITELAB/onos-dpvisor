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
package org.onosproject.net.table.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.IdGenerator;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.provider.AbstractListenerProviderRegistry;
import org.onosproject.net.provider.AbstractProviderService;
import org.onosproject.net.table.CompletedTableBatchOperation;
import org.onosproject.net.table.DefaultFlowTable;
import org.onosproject.net.table.DefaultFlowTableEntry;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableBatchEntry;
import org.onosproject.net.table.FlowTableBatchEvent;
import org.onosproject.net.table.FlowTableBatchOperation;
import org.onosproject.net.table.FlowTableBatchRequest;
import org.onosproject.net.table.FlowTableEntry;
import org.onosproject.net.table.FlowTableEvent;
import org.onosproject.net.table.FlowTableId;
import org.onosproject.net.table.FlowTableListener;
import org.onosproject.net.table.FlowTableOperation;
import org.onosproject.net.table.FlowTableOperations;
import org.onosproject.net.table.FlowTableOperationsContext;
import org.onosproject.net.table.FlowTableProvider;
import org.onosproject.net.table.FlowTableProviderRegistry;
import org.onosproject.net.table.FlowTableProviderService;
import org.onosproject.net.table.FlowTableService;
import org.onosproject.net.table.FlowTableStore;
import org.onosproject.net.table.FlowTableStoreDelegate;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;


/**
 * Provides implementation of the table NB &amp; SB APIs.
 */
@Component(immediate = true, enabled = true)
@Service
public class FlowTableManager
        extends AbstractListenerProviderRegistry<FlowTableEvent, FlowTableListener,
        FlowTableProvider, FlowTableProviderService>
        implements FlowTableService, FlowTableProviderRegistry {

    private final Logger log = getLogger(getClass());

    public static final String FLOW_TABLE_NULL = "FlowTable cannot be null";
    private static final boolean ALLOW_EXTRANEOUS_TABLES = false;

    @Property(name = "allowExtraneousTables", boolValue = ALLOW_EXTRANEOUS_TABLES,
            label = "Allow flow tables in switch not installed by ONOS")
    private boolean allowExtraneousTables = ALLOW_EXTRANEOUS_TABLES;

    @Property(name = "purgeOnDisconnection", boolValue = false,
            label = "Purge entries associated with a device when the device goes offline")
    private boolean purgeOnDisconnection = false;

    private static final int DEFAULT_POLL_FREQUENCY = 30;
    @Property(name = "fallbackFlowPollFrequency", intValue = DEFAULT_POLL_FREQUENCY,
            label = "Frequency (in seconds) for polling flow statistics via fallback provider")
    private int fallbackFlowPollFrequency = DEFAULT_POLL_FREQUENCY;

    private final FlowTableStoreDelegate delegate = new FlowTableManager.InternalStoreDelegate();
    private final DeviceListener deviceListener = new InternalDeviceListener();

    private final FlowTableDriverProvider defaultProvider = new FlowTableDriverProvider();

    protected ExecutorService deviceInstallers =
            Executors.newFixedThreadPool(32, groupedThreads("onos/tableservice", "device-installer-%d", log));

    protected ExecutorService operationsService =
            Executors.newFixedThreadPool(32, groupedThreads("onos/tableservice", "operations-%d", log));

    private IdGenerator idGenerator;

    private Map<Long, FlowOperationsProcessor> pendingFlowOperations
            = new ConcurrentHashMap<>();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore store;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService cfgService;

    @Activate
    public void activate(ComponentContext context) {
        modified(context);
        store.setDelegate(delegate);
        eventDispatcher.addSink(FlowTableEvent.class, listenerRegistry);
        deviceService.addListener(deviceListener);
        cfgService.registerProperties(getClass());
        idGenerator = coreService.getIdGenerator(FLOW_OP_TOPIC);
        log.info("Started FlowTableManager!");
    }

    @Deactivate
    public void deactivate() {
        deviceService.removeListener(deviceListener);
        cfgService.unregisterProperties(getClass(), false);
        deviceInstallers.shutdownNow();
        operationsService.shutdownNow();
        store.unsetDelegate(delegate);
        eventDispatcher.removeSink(FlowTableEvent.class);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        if (context != null) {
            readComponentConfiguration(context);
        }
        defaultProvider.init(new InternalFlowTableProviderService(defaultProvider),
                             deviceService, mastershipService, fallbackFlowPollFrequency);
    }

    @Override
    protected FlowTableProvider defaultProvider() {
        return defaultProvider;
    }

    /**
     * Extracts properties from the component configuration context.
     *
     * @param context the component context
     */
    private void readComponentConfiguration(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();
        Boolean flag;

        flag = Tools.isPropertyEnabled(properties, "allowExtraneousTables");
        if (flag == null) {
            log.info("allowExtraneousTables is not configured, " +
                    "using current value of {}", allowExtraneousTables);
        } else {
            allowExtraneousTables = flag;
            log.info("Configured. allowExtraneousTables is {}",
                    allowExtraneousTables ? "enabled" : "disabled");
        }

        flag = Tools.isPropertyEnabled(properties, "purgeOnDisconnection");
        if (flag == null) {
            log.info("PurgeOnDisconnection is not configured, " +
                    "using current value of {}", purgeOnDisconnection);
        } else {
            purgeOnDisconnection = flag;
            log.info("Configured. PurgeOnDisconnection is {}",
                    purgeOnDisconnection ? "enabled" : "disabled");
        }

        String s = get(properties, "fallbackFlowPollFrequency");
        try {
            fallbackFlowPollFrequency = isNullOrEmpty(s) ? DEFAULT_POLL_FREQUENCY : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            fallbackFlowPollFrequency = DEFAULT_POLL_FREQUENCY;
        }
    }


    @Override
    public int getNewGlobalFlowTableId(DeviceId deviceId, OFTableType type) {
        return store.getNewGlobalFlowTableId(deviceId, type);
    }

    @Override
    public int getNewFlowEntryId(DeviceId deviceId, int tableId) {
        return store.getNewFlowEntryId(deviceId, tableId);
    }

    @Override
    public int getFlowTableCount() {
        return store.getFlowTableCount();
    }

    @Override
    public Iterable<FlowTable> getFlowTables(DeviceId deviceId) {
        return store.getFlowTables(deviceId);
    }

    @Override
    public void applyFlowTables(FlowTable... flowTables) {
        FlowTableOperations.Builder builder = FlowTableOperations.builder();
        for (FlowTable flowTable : flowTables) {
            builder.add(flowTable);
        }
        apply(builder.build());
    }
    @Override
    public void applyTable(DeviceId deviceId, OFFlowTable flowTable, ApplicationId appId) {
        // added by hdy
        log.info("+++++ before applyTable");
        FlowTableProvider flowTableProvider = getProvider(deviceId);
        if (flowTableProvider != null) {
            log.info("+++++ before build flowtable");
            FlowTable flowTable1 =  DefaultFlowTable.builder()
                    .forDevice(deviceId)
                    .forTable(flowTable.getTableId())
                    .withFlowTable(flowTable)
                    .fromApp(appId)
                    .build();
            store.storeFlowTable(flowTable1);
        }
    }

    @Override
    public void removeFlowTables(FlowTable... flowTables) {
        log.info("++++ removeFlowTables");
        FlowTableOperations.Builder builder = FlowTableOperations.builder();
        for (FlowTable flowTable : flowTables) {
            if (store.getFlowEntries(flowTable.deviceId(), flowTable.id()) != null) {
                for (FlowRule rule : store.getFlowEntries(flowTable.deviceId(), flowTable.id()).values()) {
                    log.info("++++ remove FlowRule with tableId: {}", flowTable.id().value());
                    flowRuleService.removeFlowRules(rule);
                }
                try {
                    Thread thread = Thread.currentThread();
                    thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                log.info("no flow rule in table: {}", flowTable.id());
            }
            builder.remove(flowTable);
        }
        FlowTableOperations ops = builder.build();
        apply(ops);
    }

    @Override
    public void removeFlowTablesById(ApplicationId id) {
        removeFlowTables(Iterables.toArray(getFlowTablesById(id), FlowTable.class));
    }

    @Override
    public void removeFlowTablesByTableId(DeviceId deviceId, FlowTableId tableId) {
        if (getFlowTablesByTableId(deviceId, tableId) == null) {
            log.warn("DeviceID: {}, TableID: {} is does not exit!", deviceId, tableId);
        } else {
            removeFlowTables(getFlowTablesByTableId(deviceId, tableId));
        }
    }

    @Override
    public FlowTable getFlowTablesByTableId(DeviceId deviceId, FlowTableId tableId) {

        FlowTable getFlowTable = store.getFlowTableInternal(deviceId, tableId);
        return getFlowTable;
    }

    @Override
    public Iterable<FlowTable> getFlowTablesById(ApplicationId id) {

        Set<FlowTable> flowTables = Sets.newHashSet();
        for (Device d : deviceService.getDevices()) {
            for (FlowTable flowTable : store.getFlowTables(d.id())) {
                if (flowTable.appId() == id.id()) {
                    flowTables.add(flowTable);
                }
            }
        }
        return flowTables;
    }

    @Override
    public void removeFlowEntryByEntryId(DeviceId deviceId, int globalTableId, long entryId) {
        FlowRule flowRule = store.getFlowEntries(deviceId, FlowTableId.valueOf(globalTableId))
            .get(Integer.valueOf((int) entryId));
        log.info("before get entryid:{}", store.getFlowEntries(deviceId, FlowTableId.valueOf(globalTableId))
                .toString());
        log.info("tablemanager flowrule:{}", flowRule.toString());
        flowRuleService.removeFlowRules(flowRule);
    }

    @Override
    public void apply(FlowTableOperations ops) {
        log.info("+++++ table manager apply");
        operationsService.execute(new FlowOperationsProcessor(ops));
    }

    @Override
    protected FlowTableProviderService createProviderService(
            FlowTableProvider provider) {
        return new InternalFlowTableProviderService(provider);
    }

    private class InternalFlowTableProviderService
            extends AbstractProviderService<FlowTableProvider>
            implements FlowTableProviderService {

        final Map<FlowTable, Long> lastSeen = Maps.newConcurrentMap();

        protected InternalFlowTableProviderService(FlowTableProvider provider) {
            super(provider);
        }

        @Override
        public void flowTableRemoved(FlowTable flowTable) {
            checkNotNull(flowTable, FLOW_TABLE_NULL);
            checkValidity();
            lastSeen.remove(flowTable);
            FlowTableEntry stored = (FlowTableEntry) store.getFlowTable(flowTable);
            if (stored == null) {
                log.debug("table already evicted from store: {}", flowTable);
                return;
            }
            Device device = deviceService.getDevice(flowTable.deviceId());
            FlowTableProvider frp = getProvider(device.providerId());
            FlowTableEvent event = null;
            switch (stored.state()) {
                case ADDED:
                case PENDING_ADD:
                    frp.applyFlowTable((FlowTable) stored);
                    break;
                case PENDING_REMOVE:
                case REMOVED:
                    event = store.removeFlowTable(stored);
                    break;
                default:
                    break;

            }
            if (event != null) {
                log.debug("Flow {} removed", flowTable);
                post(event);
            }
        }

        private void flowMissing(FlowTableEntry flowTable) {
            checkNotNull(flowTable, FLOW_TABLE_NULL);
            checkValidity();
            Device device = deviceService.getDevice(flowTable.deviceId());
            FlowTableProvider frp = getProvider(device.providerId());
            FlowTableEvent event = null;
            switch (flowTable.state()) {
                case PENDING_REMOVE:
                case REMOVED:
                    event = store.removeFlowTable(flowTable);
                    break;
                case ADDED:
                case PENDING_ADD:
                    event = store.pendingFlowTable(flowTable);
                    try {
                        frp.applyFlowTable(flowTable);
                    } catch (UnsupportedOperationException e) {
                        log.warn(e.getMessage());
                        if (flowTable instanceof DefaultFlowTable) {
                            //FIXME modification of "stored" flow entry outside of store
                            ((DefaultFlowTableEntry) flowTable).setState(FlowTableEntry.FlowTableState.FAILED);
                        }
                    }
                    break;
                default:
                    log.debug("Flow {} has not been installed.", flowTable);
            }

            if (event != null) {
                log.debug("Flow {} removed", flowTable);
                post(event);
            }
        }

        private void extraneousFlow(FlowTable flowTable) {
            checkNotNull(flowTable, FLOW_TABLE_NULL);
            checkValidity();
            FlowTableProvider frp = getProvider(flowTable.deviceId());
            frp.removeFlowTable(flowTable);
            log.debug("Flow {} is on switch but not in store.", flowTable);
        }

        private void flowAdded(FlowTableEntry flowTable) {
            checkNotNull(flowTable, FLOW_TABLE_NULL);
            checkValidity();

            if (checkTableLiveness(flowTable, (FlowTableEntry) store.getFlowTable(flowTable))) {
                FlowTableEvent event = store.addOrUpdateFlowTable(flowTable);
                if (event == null) {
                    log.debug("No flow store event generated.");
                } else {
                    log.trace("Flow {} {}", flowTable, event.type());
                    post(event);
                }
            } else {
                log.debug("Removing flow tables....");
                removeFlowTables(flowTable);
            }
        }

        private boolean checkTableLiveness(FlowTableEntry swTable, FlowTableEntry storedTable) {
            if (storedTable == null) {
                return false;
            }
            return true;
        }

        @Override
        public void pushTableMetrics(DeviceId deviceId, Iterable<FlowTableEntry> flowTables) {
            pushTableMetricsInternal(deviceId, flowTables, true);
        }

        @Override
        public void pushTableMetricsWithoutFlowMissing(DeviceId deviceId, Iterable<FlowTableEntry> flowTables) {
            pushTableMetricsInternal(deviceId, flowTables, false);
        }


        private void pushTableMetricsInternal(DeviceId deviceId, Iterable<FlowTableEntry> flowTables,
                                             boolean useMissingFlow) {
            Map<FlowTableEntry, FlowTableEntry> storedTables = Maps.newHashMap();
            store.getFlowTables(deviceId).forEach(f -> storedTables.put((FlowTableEntry) f, (FlowTableEntry) f));

            for (FlowTableEntry table : flowTables) {
                try {
                    FlowTableEntry storedTable = storedTables.remove(table);
                    if (storedTable != null) {
                        if (storedTable.exactMatch(table)) {
                            // we both have the table, let's update some info then.
                            flowAdded(table);
                        } else {
                            // the two tables are not an exact match - remove the
                            // switch's table and install our table
                            extraneousFlow(table);
                            flowMissing(storedTable);
                        }
                    } else {
                        // the device has a table the store does not have
                        if (!allowExtraneousTables) {
                            extraneousFlow(table);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Can't process added or extra table {}", e.getMessage());
                }
            }
            // DO NOT reinstall
            if (useMissingFlow) {
                for (FlowTableEntry table : storedTables.keySet()) {
                    try {
                        // there are tables in the store that aren't on the switch
                        log.debug("Adding table in store, but not on switch {}", table);
                        flowMissing(table);
                    } catch (Exception e) {
                        log.debug("Can't add missing flow table:", e);
                    }
                }
            }
        }


        @Override
        public void batchOperationCompleted(long batchId, CompletedTableBatchOperation operation) {
            store.batchOperationComplete(FlowTableBatchEvent.completed(
                    new FlowTableBatchRequest(batchId, Collections.emptySet()),
                    operation
            ));
        }

    }

    // Store delegate to re-post events emitted from the store.
    private class InternalStoreDelegate implements FlowTableStoreDelegate {


        // TODO: Right now we only dispatch events at individual flowEntry level.
        // It may be more efficient for also dispatch events as a batch.
        @Override
        public void notify(FlowTableBatchEvent event) {
            final FlowTableBatchRequest request = event.subject();
            switch (event.type()) {
            case BATCH_OPERATION_REQUESTED:
                // Request has been forwarded to MASTER Node, and was
                request.ops().stream().forEach(
                        op -> {
                            switch (op.operator()) {

                                case ADD:
                                    post(new FlowTableEvent(FlowTableEvent.Type.TABLE_ADD_REQUESTED,
                                                           op.target()));
                                    break;
                                case REMOVE:
                                    post(new FlowTableEvent(FlowTableEvent.Type.TABLE_REMOVE_REQUESTED,
                                                           op.target()));
                                    break;
                                case MODIFY:
                                    //TODO: do something here when the time comes.
                                    break;
                                default:
                                    log.warn("Unknown flow operation operator: {}", op.operator());
                            }
                        }
                );

                DeviceId deviceId = event.deviceId();

                FlowTableBatchOperation batchOperation =
                        request.asBatchOperation(deviceId);

                FlowTableProvider flowTableProvider = getProvider(deviceId);
                if (flowTableProvider != null) {
                    flowTableProvider.executeBatch(batchOperation);
                }

                break;

            case BATCH_OPERATION_COMPLETED:

                FlowOperationsProcessor fops = pendingFlowOperations.remove(
                        event.subject().batchId());
                if (event.result().isSuccess()) {
                    if (fops != null) {
                        fops.satisfy(event.deviceId());
                    }
                } else {
                    fops.fail(event.deviceId(), event.result().failedItems());
                }

                break;

            default:
                break;
            }
        }
    }

    private class FlowOperationsProcessor implements Runnable {

        private final List<Set<FlowTableOperation>> stages;
        private final FlowTableOperationsContext context;
        private final FlowTableOperations fops;
        private final AtomicBoolean hasFailed = new AtomicBoolean(false);

        private Set<DeviceId> pendingDevices;
        public FlowOperationsProcessor() {
            this.stages = Lists.newArrayList();
            this.context = null;
            this.fops = null;
            pendingDevices = Sets.newConcurrentHashSet();
        }

        public FlowOperationsProcessor(FlowTableOperations ops) {
            this.stages = Lists.newArrayList(ops.stages());
            this.context = ops.callback();
            this.fops = ops;
            pendingDevices = Sets.newConcurrentHashSet();
        }

        @Override
        public void run() {
            log.info("+++++ FlowOperationsProcessor.run()");
            if (stages.size() > 0) {
                process(stages.remove(0));
            } else if (!hasFailed.get() && context != null) {
                context.onSuccess(fops);
            }
        }

        private void process(Set<FlowTableOperation> ops) {
            Multimap<DeviceId, FlowTableBatchEntry> perDeviceBatches =
                    ArrayListMultimap.create();

            FlowTableBatchEntry fbe;
            for (FlowTableOperation flowTableOperation : ops) {
                switch (flowTableOperation.type()) {
                    // FIXME: Brian needs imagination when creating class names.
                    case ADD:
                        fbe = new FlowTableBatchEntry(
                                FlowTableBatchEntry.FlowTableOperation.ADD, flowTableOperation.table());
                        break;
                    case MODIFY:
                        fbe = new FlowTableBatchEntry(
                                FlowTableBatchEntry.FlowTableOperation.MODIFY, flowTableOperation.table());
                        break;
                    case REMOVE:
                        fbe = new FlowTableBatchEntry(
                                FlowTableBatchEntry.FlowTableOperation.REMOVE, flowTableOperation.table());
                        break;
                    default:
                        throw new UnsupportedOperationException("Unknown flow table type " + flowTableOperation.type());
                }
                pendingDevices.add(flowTableOperation.table().deviceId());
                perDeviceBatches.put(flowTableOperation.table().deviceId(), fbe);
            }


            for (DeviceId deviceId : perDeviceBatches.keySet()) {
                long id = idGenerator.getNewId();
                final FlowTableBatchOperation b = new FlowTableBatchOperation(perDeviceBatches.get(deviceId),
                                               deviceId, id);
                pendingFlowOperations.put(id, this);
                deviceInstallers.execute(() -> store.storeBatch(b));
            }
        }

        public void satisfy(DeviceId devId) {
            pendingDevices.remove(devId);
            if (pendingDevices.isEmpty()) {
                operationsService.execute(this);
            }
        }



        public void fail(DeviceId devId, Set<? extends FlowTable> failures) {
            hasFailed.set(true);
            pendingDevices.remove(devId);
            if (pendingDevices.isEmpty()) {
                operationsService.execute(this);
            }

            if (context != null) {
                final FlowTableOperations.Builder failedOpsBuilder =
                        FlowTableOperations.builder();
                failures.stream().forEach(failedOpsBuilder::add);

                context.onError(failedOpsBuilder.build());
            }
        }

    }


    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            switch (event.type()) {
                case DEVICE_REMOVED:
                case DEVICE_AVAILABILITY_CHANGED:
                    DeviceId deviceId = event.subject().id();
                    if (!deviceService.isAvailable(deviceId)) {
                        if (purgeOnDisconnection) {
                            store.purgeFlowTable(deviceId);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
