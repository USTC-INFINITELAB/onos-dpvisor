/*
* Copyright 2015-present Open Networking Laboratory
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
package org.onosproject.store.table.flow.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.util.KryoNamespace;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.CoreService;
import org.onosproject.core.IdGenerator;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.floodlightpof.protocol.table.OFTableResource;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.table.DeviceOFTableType;
import org.onosproject.net.table.DeviceTableId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TableStatisticsEntry;
import org.onosproject.net.table.CompletedTableBatchOperation;
import org.onosproject.net.table.DefaultFlowTableEntry;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableBatchEntry;
import org.onosproject.net.table.FlowTableBatchEntry.FlowTableOperation;
import org.onosproject.net.table.FlowTableBatchEvent;
import org.onosproject.net.table.FlowTableBatchOperation;
import org.onosproject.net.table.FlowTableBatchRequest;
import org.onosproject.net.table.FlowTableEntry;
import org.onosproject.net.table.FlowTableEvent;
import org.onosproject.net.table.FlowTableId;
import org.onosproject.net.table.FlowTableService;
import org.onosproject.net.table.FlowTableStore;
import org.onosproject.net.table.FlowTableStoreDelegate;
import org.onosproject.net.table.StoredFlowTableEntry;
import org.onosproject.persistence.PersistenceService;
import org.onosproject.store.AbstractStore;
import org.onosproject.store.cluster.messaging.ClusterCommunicationService;
import org.onosproject.store.cluster.messaging.ClusterMessage;
import org.onosproject.store.cluster.messaging.ClusterMessageHandler;
import org.onosproject.store.flow.ReplicaInfoEvent;
import org.onosproject.store.flow.ReplicaInfoEventListener;
import org.onosproject.store.flow.ReplicaInfoService;
import org.onosproject.store.impl.MastershipBasedTimestamp;
import org.onosproject.store.serializers.KryoNamespaces;
//import org.onosproject.store.serializers.KryoSerializer;
//import org.onosproject.store.serializers.StoreSerializer;
import org.onosproject.store.serializers.custom.DistributedStoreSerializers;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.EventuallyConsistentMapEvent;
import org.onosproject.store.service.EventuallyConsistentMapListener;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.table.FlowTableEvent.Type.TABLE_REMOVED;
import static org.onosproject.store.table.flow.impl.FlowTableStoreMessageSubjects.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Manages inventory of flow rules using a distributed state management protocol.
 */
@Component(immediate = true)
@Service
public class NewDistributedFlowTableStore
        extends AbstractStore<FlowTableBatchEvent, FlowTableStoreDelegate>
        implements FlowTableStore {

    private final Logger log = getLogger(getClass());

    private static final int MESSAGE_HANDLER_THREAD_POOL_SIZE = 8;
    private static final boolean DEFAULT_BACKUP_ENABLED = false;
    private static final boolean DEFAULT_PERSISTENCE_ENABLED = false;
    private static final int DEFAULT_BACKUP_PERIOD_MILLIS = 2000;
    private static final long FLOW_TABLE_STORE_TIMEOUT_MILLIS = 5000;
    private static final long GET_NEW_GLOBALTABLEID_TIMEOUT_MILLIS = 5000;
    private static final long GET_NEW_FLOWENTRYID_TIMEOUT_MILLIS = 5000;
    // number of devices whose flow entries will be backed up in one communication round
    private static final int FLOW_TABLE_BACKUP_BATCH_SIZE = 1;

    @Property(name = "msgHandlerPoolSize", intValue = MESSAGE_HANDLER_THREAD_POOL_SIZE,
            label = "Number of threads in the message handler pool")
    private int msgHandlerPoolSize = MESSAGE_HANDLER_THREAD_POOL_SIZE;

    @Property(name = "backupEnabled", boolValue = DEFAULT_BACKUP_ENABLED,
            label = "Indicates whether backups are enabled or not")
    private boolean backupEnabled = DEFAULT_BACKUP_ENABLED;

    @Property(name = "backupPeriod", intValue = DEFAULT_BACKUP_PERIOD_MILLIS,
            label = "Delay in ms between successive backup runs")
    private int backupPeriod = DEFAULT_BACKUP_PERIOD_MILLIS;
    @Property(name = "persistenceEnabled", boolValue = false,
            label = "Indicates whether or not changes in the flow table should be persisted to disk.")
    private boolean persistenceEnabled = DEFAULT_PERSISTENCE_ENABLED;

    private InternalFlowTable flowTable = new InternalFlowTable();

    private Map<DeviceId, Map<FlowTableId, Map<Integer, FlowRule>>>
            flowEntries = Maps.newConcurrentMap();

    private ConsistentMap<DeviceId,Map<FlowTableId, List<Integer>>>freeFlowEntryIdConsistentMap;
    private Map<DeviceId,Map<FlowTableId, List<Integer>>>freeFlowEntryIdMap;

    private Map<FlowTableId, List<Integer>>freeFlowEntryIdTmpMap = new HashMap<>();

    private ConsistentMap<DeviceId,Map<FlowTableId, Integer>> flowEntryCountConsistentMap;
    private Map<DeviceId,Map<FlowTableId, Integer>> flowEntryCountMap = Maps.newConcurrentMap();

    private Map<FlowTableId, Integer> flowEntryCountTmpMap = new HashMap<>();

    private ConsistentMap<DeviceId, Map<OFTableType,Byte>>flowTableNoBaseConsistentMap;
    private Map<DeviceId,Map<OFTableType, Byte>>flowTableNoBaseMap = Maps.newConcurrentMap();

    private ConsistentMap<DeviceId, Map<OFTableType, Byte>>flowTableNoConsistentMap;
    private Map<DeviceId, Map<OFTableType, Byte>> flowTableNoMap = Maps.newConcurrentMap();

    private ConsistentMap<DeviceId, Map<OFTableType, List<Byte>>> freeFlowTableIdListConsistentMap;
    private Map<DeviceId, Map<OFTableType, List<Byte>>> freeFlowTableIdListMap = Maps.newConcurrentMap();

    private ConsistentMap<DeviceId, Map<FlowTableId, StoredFlowTableEntry>>flowTablesConsistMap;
    private Map<DeviceId, Map<FlowTableId, StoredFlowTableEntry>> flowTablesMap = Maps.newConcurrentMap();

    private Map<FlowTableId, StoredFlowTableEntry>flowTablesTmpMap = new HashMap<>();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ReplicaInfoService replicaInfoManager;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterCommunicationService clusterCommunicator;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PersistenceService persistenceService;


    private Map<Long, NodeId> pendingResponses = Maps.newConcurrentMap();
    private ExecutorService messageHandlingExecutor;
    private ScheduledFuture<?> backupTask;
    private final ScheduledExecutorService backupSenderExecutor =
            Executors.newSingleThreadScheduledExecutor(groupedThreads("onos/table", "backup-sender", log));

    private EventuallyConsistentMap<DeviceId, List<TableStatisticsEntry>> deviceTableStats;

    private final EventuallyConsistentMapListener<DeviceId, List<TableStatisticsEntry>> tableStatsListener =
            new InternalTableStatsListener();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    protected static final Serializer SERIALIZER = Serializer.using(
            KryoNamespace.newBuilder()
                    .register(DistributedStoreSerializers.STORE_COMMON)
                    .nextId(DistributedStoreSerializers.STORE_CUSTOM_BEGIN)
                    .build()
    );

    protected static final KryoNamespace.Builder SERIALIZER_BUILDER = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(MastershipBasedTimestamp.class);


    private IdGenerator idGenerator;
    private NodeId local;

    @Activate
    public void activate(ComponentContext context) {
        configService.registerProperties(getClass());

        idGenerator = coreService.getIdGenerator(FlowTableService.FLOW_OP_TOPIC);

        local = clusterService.getLocalNode().id();

        messageHandlingExecutor = Executors.newFixedThreadPool(
                msgHandlerPoolSize, groupedThreads("onos/store/table", "message-handlers", log));

        registerMessageHandlers(messageHandlingExecutor);

        if (backupEnabled) {
            replicaInfoManager.addListener(flowTable);
            backupTask = backupSenderExecutor.scheduleWithFixedDelay(
                    flowTable::backup,
                    0,
                    backupPeriod,
                    TimeUnit.MILLISECONDS);
        }

        deviceTableStats = storageService.<DeviceId, List<TableStatisticsEntry>>eventuallyConsistentMapBuilder()
                .withName("onos-flow-table-stats")
                .withSerializer(SERIALIZER_BUILDER)
                .withAntiEntropyPeriod(5, TimeUnit.SECONDS)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .withTombstonesDisabled()
                .build();

        freeFlowEntryIdConsistentMap = storageService.<DeviceId,Map<FlowTableId,List<Integer>>>consistentMapBuilder()
                .withName("onos-freeflow-entryids")
                .withSerializer(SERIALIZER)
                .withRelaxedReadConsistency()
                .build();
        freeFlowEntryIdMap = freeFlowEntryIdConsistentMap.asJavaMap();

        flowEntryCountConsistentMap = storageService.<DeviceId,Map<FlowTableId,Integer>>consistentMapBuilder()
                .withName("onos-freeflow-count")
                .withSerializer(SERIALIZER)
                .withRelaxedReadConsistency()
                .build();
        flowEntryCountMap=flowEntryCountConsistentMap.asJavaMap();

        flowTableNoBaseConsistentMap = storageService.<DeviceId,Map<OFTableType,Byte>>consistentMapBuilder()
                .withName("onos-flowtable-nobase")
                .withSerializer(SERIALIZER)
                .withRelaxedReadConsistency()
                .build();
        flowTableNoBaseMap=flowTableNoBaseConsistentMap.asJavaMap();

        flowTableNoConsistentMap = storageService.<DeviceId,Map<OFTableType,Byte>>consistentMapBuilder()
                .withName("onos-flowtable-no")
                .withSerializer(SERIALIZER)
                .withRelaxedReadConsistency()
                .build();
        flowTableNoMap =flowTableNoConsistentMap.asJavaMap();

        freeFlowTableIdListConsistentMap = storageService.<DeviceId,Map<OFTableType,List<Byte>>>consistentMapBuilder()
                .withName("onos-flowtable-list")
                .withSerializer(SERIALIZER)
                .withRelaxedReadConsistency()
                .build();
        freeFlowTableIdListMap = freeFlowTableIdListConsistentMap.asJavaMap();

        flowTablesConsistMap=storageService.<DeviceId,Map<FlowTableId,StoredFlowTableEntry>>consistentMapBuilder()
                .withName("onos-flowtables-store")
                .withRelaxedReadConsistency()
                .withSerializer(SERIALIZER)
                .build();
        flowTablesMap=flowTablesConsistMap.asJavaMap();

        deviceTableStats.addListener(tableStatsListener);

        logConfig("Started");
    }

    @Deactivate
    public void deactivate(ComponentContext context) {
        if (backupEnabled) {
            replicaInfoManager.removeListener(flowTable);
            backupTask.cancel(true);
        }
        configService.unregisterProperties(getClass(), false);
        unregisterMessageHandlers();
        deviceTableStats.removeListener(tableStatsListener);
        deviceTableStats.destroy();
        messageHandlingExecutor.shutdownNow();
        backupSenderExecutor.shutdownNow();
        log.info("Stopped");
    }

    @SuppressWarnings("rawtypes")
    @Modified
    public void modified(ComponentContext context) {
        if (context == null) {
            backupEnabled = DEFAULT_BACKUP_ENABLED;
            logConfig("Default config");
            return;
        }

        Dictionary properties = context.getProperties();
        int newPoolSize;
        boolean newBackupEnabled;
        int newBackupPeriod;
        try {
            String s = get(properties, "msgHandlerPoolSize");
            newPoolSize = isNullOrEmpty(s) ? msgHandlerPoolSize : Integer.parseInt(s.trim());

            s = get(properties, "backupEnabled");
            newBackupEnabled = isNullOrEmpty(s) ? backupEnabled : Boolean.parseBoolean(s.trim());

            s = get(properties, "backupPeriod");
            newBackupPeriod = isNullOrEmpty(s) ? backupPeriod : Integer.parseInt(s.trim());

        } catch (NumberFormatException | ClassCastException e) {
            newPoolSize = MESSAGE_HANDLER_THREAD_POOL_SIZE;
            newBackupEnabled = DEFAULT_BACKUP_ENABLED;
            newBackupPeriod = DEFAULT_BACKUP_PERIOD_MILLIS;
        }

        boolean restartBackupTask = false;
        if (newBackupEnabled != backupEnabled) {
            backupEnabled = newBackupEnabled;
            if (!backupEnabled) {
                replicaInfoManager.removeListener(flowTable);
                if (backupTask != null) {
                    backupTask.cancel(false);
                    backupTask = null;
                }
            } else {
                replicaInfoManager.addListener(flowTable);
            }
            restartBackupTask = backupEnabled;
        }
        if (newBackupPeriod != backupPeriod) {
            backupPeriod = newBackupPeriod;
            restartBackupTask = backupEnabled;
        }
        if (restartBackupTask) {
            if (backupTask != null) {
                // cancel previously running task
                backupTask.cancel(false);
            }
            backupTask = backupSenderExecutor.scheduleWithFixedDelay(
                    flowTable::backup,
                    0,
                    backupPeriod,
                    TimeUnit.MILLISECONDS);
        }
        if (newPoolSize != msgHandlerPoolSize) {
            msgHandlerPoolSize = newPoolSize;
            ExecutorService oldMsgHandler = messageHandlingExecutor;
            messageHandlingExecutor = Executors.newFixedThreadPool(
                    msgHandlerPoolSize, groupedThreads("onos/store/table", "message-handlers", log));

            // replace previously registered handlers.
            registerMessageHandlers(messageHandlingExecutor);
            oldMsgHandler.shutdown();
        }
        logConfig("Reconfigured");
    }

    private void registerMessageHandlers(ExecutorService executor) {

        clusterCommunicator.addSubscriber(APPLY_BATCH_TABLES, new OnStoreBatch(), executor);
        clusterCommunicator.<FlowTableBatchEvent>addSubscriber(
                REMOTE_APPLY_COMPLETED, SERIALIZER::decode, this::notifyDelegate, executor);
        clusterCommunicator.addSubscriber(
                GET_FLOW_TABLE, SERIALIZER::decode, flowTable::getFlowTable, SERIALIZER::encode, executor);
        clusterCommunicator.addSubscriber(
                GET_DEVICE_FLOW_TABLES, SERIALIZER::decode, flowTable::getFlowTables, SERIALIZER::encode, executor);
        clusterCommunicator.addSubscriber(
                REMOVE_FLOW_TABLE, SERIALIZER::decode, this::removeFlowTableInternal, SERIALIZER::encode, executor);
        clusterCommunicator.addSubscriber(
                FLOW_TABLE_BACKUP, SERIALIZER::decode, flowTable::onBackupReceipt, SERIALIZER::encode, executor);
        clusterCommunicator.addSubscriber(
                GET_NEW_GLOBAL_TABLEID, SERIALIZER::decode, flowTable::getGlobalFlowTableId, SERIALIZER::encode, executor);
        clusterCommunicator.addSubscriber(
                GET_NEW_GLOBAL_ENTRYID, SERIALIZER::decode, flowTable::getFlowEntryId, SERIALIZER::encode, executor);
    }

    private void unregisterMessageHandlers() {
        clusterCommunicator.removeSubscriber(REMOVE_FLOW_TABLE);
        clusterCommunicator.removeSubscriber(GET_DEVICE_FLOW_TABLES);
        clusterCommunicator.removeSubscriber(GET_FLOW_TABLE);
        clusterCommunicator.removeSubscriber(APPLY_BATCH_TABLES);
        clusterCommunicator.removeSubscriber(REMOTE_APPLY_COMPLETED);
        clusterCommunicator.removeSubscriber(FLOW_TABLE_BACKUP);
        clusterCommunicator.removeSubscriber(GET_NEW_GLOBAL_TABLEID);
        clusterCommunicator.removeSubscriber(GET_NEW_GLOBAL_ENTRYID);
    }

    private void logConfig(String prefix) {
        log.info("{} with msgHandlerPoolSize = {}; backupEnabled = {}, backupPeriod = {}",
                 prefix, msgHandlerPoolSize, backupEnabled, backupPeriod);
    }

    /**
     * Initialize flowtables and flowentries store.
     * */
    @Override
    public void initializeSwitchStore(DeviceId deviceId) {

        log.info("initializeSwitchStore for device: {}", deviceId);
        if (flowEntryCountMap.containsKey(deviceId)) {
            //TODO nothing
        } else {

            Map<FlowTableId, Integer> tcount = new ConcurrentHashMap<>();
            tcount.put(FlowTableId.valueOf(0),0);
            flowEntryCountMap.putIfAbsent(deviceId, tcount);
        }
        if (freeFlowEntryIdMap.containsKey(deviceId)) {
            //TODO nothing
        } else {

            List<Integer> ids = new ArrayList<>();
            Map<FlowTableId, List<Integer>> tids = new ConcurrentHashMap<>();
            freeFlowEntryIdMap.putIfAbsent(deviceId, tids);//initialization map all is null
        }
    }
    //if pof switch removed all the map well be cleared
    @Override
    public void removeSwitchStore(DeviceId deviceId) {

        log.info("removeSwitchStore for device: {}", deviceId);
        flowEntryCountMap.remove(deviceId);
        freeFlowEntryIdMap.remove(deviceId);
        flowEntries.remove(deviceId);

        freeFlowTableIdListMap.remove(deviceId);
        flowTableNoMap.remove(deviceId);
        flowTableNoBaseMap.remove(deviceId);
        flowTable.removeDevice(deviceId);
    }


    // This is not a efficient operation on a distributed sharded
    // flow store. We need to revisit the need for this operation or at least
    // make it device specific.
    @Override
    public int getFlowTableCount() {
        AtomicInteger sum = new AtomicInteger(0);
        deviceService.getDevices().forEach(device -> sum.addAndGet(Iterables.size(getFlowTables(device.id()))));
        return sum.get();
    }

    @Override
    public void setFlowTableNoBase(DeviceId deviceId, OFFlowTableResource of) {
        log.info("+++++ setFlowTableNoBase for device {}", deviceId.toString());
        byte base = 0;
        OFTableResource tableResource;
        Map<OFTableType, OFTableResource> flowTableReourceMap = of.getTableResourcesMap();

        Map<OFTableType, Byte> noMap = new ConcurrentHashMap<>();
        Map<OFTableType, Byte> noBaseMap = new ConcurrentHashMap<>();
        Map<OFTableType, List<Byte>> freeIDListMap = new ConcurrentHashMap<>();

        if (flowTableReourceMap == null) {
            return;
        }
        for (byte tableType = 0; tableType < OFTableType.OF_MAX_TABLE_TYPE.getValue(); tableType++) {
            tableResource = flowTableReourceMap.get(OFTableType.values()[ tableType ]);
            noMap.put(OFTableType.values()[tableType], base);
            noBaseMap.put(OFTableType.values()[tableType], base);
            freeIDListMap.put(OFTableType.values()[tableType], new ArrayList<Byte>());
            base += tableResource.getTableNum();
        }
        this.flowTableNoMap.put(deviceId, noMap);
        this.flowTableNoBaseMap.put(deviceId, noBaseMap);
        this.freeFlowTableIdListMap.put(deviceId, freeIDListMap);
        Map<FlowTableId, Map<Integer, FlowRule>> tfs = new ConcurrentHashMap<>();//the max size of table no is 128
        for ( byte i = 0; i< base; i++) {

            Map<Integer, FlowRule>emptyTmp=new HashMap<>();
            tfs.putIfAbsent(new FlowTableId(i),emptyTmp);
        }
        flowEntries.putIfAbsent(deviceId, tfs);
    }

    @Override
    public byte parseToSmallTableId(DeviceId deviceId, int globalTableId) {
        FlowTableId tableId = FlowTableId.valueOf(globalTableId);

        if (flowTablesMap.get(deviceId) != null) {
            FlowTable flowtable = flowTablesMap.get(deviceId).get(tableId);
            if (flowtable != null) {
                return flowtable.flowTable().getTableId();
            }
        }

        for (byte tableType = (byte) (OFTableType.OF_MAX_TABLE_TYPE.getValue() - 1);
             tableType >= 0; tableType--) {
            byte flowTableNoBase = flowTableNoBaseMap.get(deviceId)
                    .get(OFTableType.values()[tableType]);

            if (flowTableNoBase == -1) {
                return flowTableNoBase;
            }

            if (tableId.value() >= flowTableNoBase) {
                return (byte) (tableId.value() - flowTableNoBase);
            }
        }
        return -1;

    }

    @Override
    public byte parseToGlobalTableId(DeviceId deviceId, OFTableType tableType, byte smallTableId) {
        byte flowTableNoBase = flowTableNoBaseMap.get(deviceId).get(tableType);
        if (flowTableNoBase == -1) {
            return flowTableNoBase;
        }
        return (byte) (flowTableNoBase + smallTableId);
    }


    @Override
    public Map<OFTableType, Byte> getFlowTableNoBaseMap(DeviceId deviceId) {
        return this.flowTableNoBaseMap.get(deviceId);
    }

    @Override
    public Map<OFTableType, Byte> getFlowTableNoMap(DeviceId deviceId) {

        return this.flowTableNoMap.get(deviceId);
    }

    @Override
    public Map<OFTableType, List<Byte>> getFreeFlowTableIDListMap(DeviceId deviceId) {
        return this.freeFlowTableIdListMap.get(deviceId);
    }

    @Override
    public int getFlowEntryCount(DeviceId deviceId, FlowTableId flowTableId) {

        return flowEntryCountMap.get(deviceId).get(flowTableId);
    }

    @Override
    public void addFlowEntryCount(DeviceId deviceId, FlowTableId flowTableId) {


        Integer tmp = flowEntryCountMap.get(deviceId).get(flowTableId);
        for(FlowTableId flowTableIdTmp:flowEntryCountMap.get(deviceId).keySet()){
            if(flowTableIdTmp.equals(flowTableId)){
                flowEntryCountTmpMap.put(flowTableIdTmp, tmp + 1);
            } else {

                flowEntryCountTmpMap.put(flowTableIdTmp, flowEntryCountMap.get(deviceId).get(flowTableIdTmp));
            }
        }
        flowEntryCountMap.put(deviceId, flowEntryCountTmpMap);


        log.info("++++addFlowEntryCount: {}", tmp + 1);

    }

    @Override
    public void deleteFlowEntryCount(DeviceId deviceId, FlowTableId flowTableId) {

        Integer tmp = flowEntryCountMap.get(deviceId).get(flowTableId);
        for(FlowTableId flowTableIdTmp:flowEntryCountMap.get(deviceId).keySet()){
            if(flowTableIdTmp.equals(flowTableId)){
                flowEntryCountTmpMap.put(flowTableIdTmp, tmp - 1);
            } else {

                flowEntryCountTmpMap.put(flowTableIdTmp, flowEntryCountMap.get(deviceId).get(flowTableIdTmp));
            }
        }

        flowEntryCountMap.put(deviceId, flowEntryCountTmpMap);


    }

    @Override
    public List<Integer> getFreeFlowEntryIds(DeviceId deviceId, FlowTableId flowTableId) {
        return freeFlowEntryIdMap.get(deviceId).get(flowTableId);
    }

    @Override
    public void addFreeFlowEntryIds(DeviceId deviceId, FlowTableId flowTableId, Integer flowEntryId) {
        freeFlowEntryIdTmpMap.clear();
        freeFlowEntryIdTmpMap.putAll(freeFlowEntryIdMap.get(deviceId));
        freeFlowEntryIdTmpMap.get(flowTableId).add(flowEntryId);
        freeFlowEntryIdMap.put(deviceId,freeFlowEntryIdTmpMap);
    }

    @Override
    public void deleteFreeFlowEntryIds(DeviceId deviceId, FlowTableId flowTableId, Integer flowEntryId) {
        freeFlowEntryIdTmpMap.clear();
        freeFlowEntryIdTmpMap.putAll(freeFlowEntryIdMap.get(deviceId));
        freeFlowEntryIdTmpMap.get(flowTableId).remove(flowEntryId);
        freeFlowEntryIdMap.put(deviceId,freeFlowEntryIdTmpMap);
    }

    public Map<FlowTableId, Map<Integer, FlowRule>> getFlowEntries(DeviceId deviceId) {
        //log.info("getFlowEntries by deviceId: {}", deviceId);
        if (flowEntries.get(deviceId) == null) {
            //log.info("no table in deviceId: {}", deviceId);
            return null;
        } else {
            return flowEntries.get(deviceId);
        }
    }

    @Override
    public Map<Integer, FlowRule> getFlowEntries(DeviceId deviceId, FlowTableId flowTableId) {
        //log.info("getFlowEntries by deviceId: {} and tableID: {}", deviceId, flowTableId.value());
        return getFlowEntries(deviceId).get(flowTableId);
    }

    @Override
    public void addFlowEntry(DeviceId deviceId, FlowTableId flowTableId, FlowRule flowRule) {

        getFlowEntries(deviceId, flowTableId).putIfAbsent((int) flowRule.id().value(), flowRule);
    }

    @Override
    public void modifyFlowEntry(DeviceId deviceId, FlowTableId flowTableId, FlowRule flowRule) {
        flowEntries.get(deviceId).get(flowTableId).replace((int) flowRule.id().value(), flowRule);
    }

    @Override
    public void deleteFlowEntry(DeviceId deviceId, FlowTableId flowTableId, int flowEntryId) {

        flowEntries.get(deviceId).get(flowTableId).remove(flowEntryId);
        log.info("before delete flow entry count");
        deleteFlowEntryCount(deviceId, flowTableId);
        addFreeFlowEntryIds(deviceId, flowTableId, flowEntryId);
        if (flowEntries.get(deviceId).get(flowTableId) == null) {
            log.info("+++++ flow entry map is null!!!");
        }
    }

    @Override
    public FlowTable getFlowTableInternal(DeviceId deviceId, FlowTableId flowTableId) {
        return flowTable.getFlowTableInternal(deviceId, flowTableId);
    }

    @Override
    public FlowTable getFlowTable(FlowTable table) {
        NodeId master = mastershipService.getMasterFor(table.deviceId());

        if (master == null) {
            log.debug("Failed to getFlowTableEntry: No master for {}", table.deviceId());
            return null;
        }

        if (Objects.equals(local, master)) {
            return flowTable.getFlowTable(table);
        }

        log.trace("Forwarding getFlowTableEntry to {}, which is the primary (master) for device {}",
                  master, table.deviceId());

        return Tools.futureGetOrElse(clusterCommunicator.sendAndReceive(table,
                                                                        FlowTableStoreMessageSubjects.GET_FLOW_TABLE,
                                                                        SERIALIZER::encode,
                                                                        SERIALIZER::decode,
                                                                        master),
                                     FLOW_TABLE_STORE_TIMEOUT_MILLIS,
                                     TimeUnit.MILLISECONDS,
                                     null);
    }

    @Override
    public Iterable<FlowTable> getFlowTables(DeviceId deviceId) {
        NodeId master = mastershipService.getMasterFor(deviceId);

        if (master == null) {
            log.debug("Failed to getFlowEntries: No master for {}", deviceId);
            return Collections.emptyList();
        }

        if (Objects.equals(local, master)) {
            Map<FlowTableId, StoredFlowTableEntry> tableMap = flowTable.getFlowTables(deviceId);

            return tableMap.values().stream()
                    .collect(Collectors.toSet());
        }

        log.trace("Forwarding getFlowEntries to {}, which is the primary (master) for device {}",
                  master, deviceId);

        return Tools.futureGetOrElse(clusterCommunicator.sendAndReceive(deviceId,
                                                                        FlowTableStoreMessageSubjects.GET_DEVICE_FLOW_TABLES,
                                                                        SERIALIZER::encode,
                                                                        SERIALIZER::decode,
                                                                        master),
                                     FLOW_TABLE_STORE_TIMEOUT_MILLIS,
                                     TimeUnit.MILLISECONDS,
                                     Collections.emptyList());
    }
    @Override
    public int getNewGlobalFlowTableId(DeviceId deviceId, OFTableType tableType) {
        DeviceOFTableType deviceOFTableType = new DeviceOFTableType(deviceId, tableType);
        NodeId master = mastershipService.getMasterFor(deviceOFTableType.getDeviceId());

        if (master == null) {
            log.debug("Failed to getGlobalTableId: No master for {}", deviceOFTableType.getDeviceId());
            return -1;
        }

        if (Objects.equals(local, master)) {
            return flowTable.getGlobalFlowTableId(deviceOFTableType);
        }

        log.trace("Forwarding getGlobalTableId to {}, which is the primary(master) for device {}",
                  master, deviceOFTableType.getDeviceId());
        return Tools.futureGetOrElse(clusterCommunicator.sendAndReceive(deviceOFTableType,
                                                                        FlowTableStoreMessageSubjects.GET_NEW_GLOBAL_TABLEID,
                                                                        SERIALIZER::encode,
                                                                        SERIALIZER::decode,
                                                                        master),
                                     GET_NEW_GLOBALTABLEID_TIMEOUT_MILLIS,
                                     TimeUnit.MILLISECONDS,
                                     0);
    }
    @Override
    public int getNewFlowEntryId(DeviceId deviceId, int tableId) {
        DeviceTableId deviceTableId = new DeviceTableId(deviceId, tableId);
        NodeId master = mastershipService.getMasterFor(deviceTableId.getDeviceId());
        if (master == null) {
            log.debug("Failed to getFLowEntryID: no master for {}", deviceTableId.getDeviceId());
            return -1;
        }

        if (Objects.equals(local, master)) {
            return flowTable.getFlowEntryId(deviceTableId);
        }

        log.info("Forwarding getFlowEntryId to {}, which is the primary(master) for device {}",
                 master, deviceTableId.getDeviceId());
        return Tools.futureGetOrElse(clusterCommunicator.sendAndReceive(deviceTableId,
                                                                        FlowTableStoreMessageSubjects.GET_NEW_GLOBAL_ENTRYID,
                                                                        SERIALIZER::encode,
                                                                        SERIALIZER::decode,
                                                                        master),
                                     GET_NEW_FLOWENTRYID_TIMEOUT_MILLIS,
                                     TimeUnit.MILLISECONDS,
                                     0);
    }

    @Override
    public void storeFlowTable(FlowTable table) {
        storeBatch(new FlowTableBatchOperation(
                Collections.singletonList(new FlowTableBatchEntry(FlowTableBatchEntry.FlowTableOperation.ADD, table)),
                table.deviceId(), idGenerator.getNewId()));
    }

    @Override
    public void storeBatch(FlowTableBatchOperation operation) {
        log.info("+++++ NewDistributedFlowTableStore.storeBatch()");
        if (operation.getOperations().isEmpty()) {
            notifyDelegate(FlowTableBatchEvent.completed(
                    new FlowTableBatchRequest(operation.id(), Collections.emptySet()),
                    new CompletedTableBatchOperation(true, Collections.emptySet(), operation.deviceId())));
            return;
        }

        DeviceId deviceId = operation.deviceId();
        NodeId master = mastershipService.getMasterFor(deviceId);

        if (master == null) {
            log.warn("No master for {} : flows will be marked for removal", deviceId);

            updateStoreInternal(operation);

            notifyDelegate(FlowTableBatchEvent.completed(
                    new FlowTableBatchRequest(operation.id(), Collections.emptySet()),
                    new CompletedTableBatchOperation(true, Collections.emptySet(), operation.deviceId())));
            return;
        }

        if (Objects.equals(local, master)) {
            storeBatchInternal(operation);
            return;
        }

        log.trace("Forwarding storeBatch to {}, which is the primary (master) for device {}",
                  master, deviceId);

        clusterCommunicator.unicast(operation,
                                    APPLY_BATCH_TABLES,
                                    SERIALIZER::encode,
                                    master)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.warn("Failed to storeBatch: {} to {}", operation, master, error);

                        Set<FlowTable> allFailures = operation.getOperations()
                                .stream()
                                .map(op -> op.target())
                                .collect(Collectors.toSet());

                        notifyDelegate(FlowTableBatchEvent.completed(
                                new FlowTableBatchRequest(operation.id(), Collections.emptySet()),
                                new CompletedTableBatchOperation(false, allFailures, deviceId)));
                    }
                });
    }

    private void storeBatchInternal(FlowTableBatchOperation operation) {

        final DeviceId did = operation.deviceId();
        Set<FlowTableBatchEntry> currentOps = updateStoreInternal(operation);
        if (currentOps.isEmpty()) {
            batchOperationComplete(FlowTableBatchEvent.completed(
                    new FlowTableBatchRequest(operation.id(), Collections.emptySet()),
                    new CompletedTableBatchOperation(true, Collections.emptySet(), did)));
            return;
        }

        notifyDelegate(FlowTableBatchEvent.requested(new
                                                             FlowTableBatchRequest(operation.id(),
                                                                                   currentOps), operation.deviceId()));
    }

    private Set<FlowTableBatchEntry> updateStoreInternal(FlowTableBatchOperation operation) {
        return operation.getOperations().stream().map(
                op -> {
                    StoredFlowTableEntry entry;
                    switch (op.operator()) {
                        case ADD:
                            entry = new DefaultFlowTableEntry(op.target());
                            // always add requested FlowTable
                            // Note: 2 equal FlowTableEntry may have different treatment
                            flowTable.remove(entry.deviceId(), entry);
                            flowTable.add(entry);

                            return op;
                        case REMOVE:
                            entry = (StoredFlowTableEntry) flowTable.getFlowTable(op.target());
                            if (entry != null) {

                                flowTable.remove(entry.deviceId(), entry);

                                //FIXME modification of "stored" flow entry outside of flow table
                                entry.setState(FlowTableEntry.FlowTableState.PENDING_REMOVE);
                                log.debug("Setting state of rule to pending remove: {}", entry);
                                return op;
                            }
                            break;
                        case MODIFY:
                            //TODO: figure this out at some point
                            break;
                        default:
                            log.warn("Unknown flow operation operator: {}", op.operator());
                    }
                    return null;
                }
        ).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    @Override
    public void deleteFlowTable(FlowTable table) {
        storeBatch(
                new FlowTableBatchOperation(
                        Collections.singletonList(
                                new FlowTableBatchEntry(
                                        FlowTableOperation.REMOVE,
                                        table)), table.deviceId(), idGenerator.getNewId()));
    }

    @Override
    public FlowTableEvent pendingFlowTable(FlowTable table) {
        if (mastershipService.isLocalMaster(table.deviceId())) {
            StoredFlowTableEntry stored = (StoredFlowTableEntry) flowTable.getFlowTable(table);
            if (stored != null &&
                    stored.state() != FlowTableEntry.FlowTableState.PENDING_ADD) {
                stored.setState(FlowTableEntry.FlowTableState.PENDING_ADD);
                return new FlowTableEvent(FlowTableEvent.Type.TABLE_UPDATED, table);
            }
        }
        return null;
    }

    @Override
    public FlowTableEvent addOrUpdateFlowTable(FlowTable table) {
        NodeId master = mastershipService.getMasterFor(table.deviceId());
        if (Objects.equals(local, master)) {
            return addOrUpdateFlowTableInternal(table);
        }

        log.warn("Tried to update FlowTable {} state,"
                         + " while the Node was not the master.", table);
        return null;
    }

    private FlowTableEvent addOrUpdateFlowTableInternal(FlowTable table) {
        // check if this new table is an update to an existing entry
        StoredFlowTableEntry stored = (StoredFlowTableEntry) flowTable.getFlowTable(table);
        if (stored != null) {
            //FIXME modification of "stored" flow entry outside of flow table
            stored.setBytes(stored.bytes());
            stored.setLife(stored.life());
            stored.setPackets(stored.packets());
            stored.setLastSeen();
            if (stored.state() == FlowTableEntry.FlowTableState.PENDING_ADD) {
                stored.setState(FlowTableEntry.FlowTableState.ADDED);
                return new FlowTableEvent(FlowTableEvent.Type.TABLE_ADDED, table);
            }
            return new FlowTableEvent(FlowTableEvent.Type.TABLE_UPDATED, table);
        }

        // TODO: Confirm if this behavior is correct. See SimpleFlowTableStore
        // TODO: also update backup if the behavior is correct.
        flowTable.add(table);
        return null;
    }



    @Override
    public FlowTableEvent removeFlowTable(FlowTable table) {
        final DeviceId deviceId = table.deviceId();
        NodeId master = mastershipService.getMasterFor(deviceId);

        if (Objects.equals(local, master)) {
            // bypass and handle it locally
            return removeFlowTableInternal(table);
        }

        if (master == null) {
            log.warn("Failed to removeFlowTable: No master for {}", deviceId);
            // TODO: revisit if this should be null (="no-op") or Exception
            return null;
        }

        log.trace("Forwarding removeFlowTable to {}, which is the master for device {}",
                  master, deviceId);

        return Futures.getUnchecked(clusterCommunicator.sendAndReceive(
                table,
                REMOVE_FLOW_TABLE,
                SERIALIZER::encode,
                SERIALIZER::decode,
                master));
    }

    private FlowTableEvent removeFlowTableInternal(FlowTable table) {
        final DeviceId deviceId = table.deviceId();
        // This is where one could mark a rule as removed and still keep it in the store.
        log.info("++++ before flowTable.remove() ");
        final FlowTableEntry removed = (FlowTableEntry) flowTable.remove(deviceId, table);
        log.info("++++ after flowTable.remove() ");
        // rule may be partial rule that is missing treatment, we should use rule from store instead
        return removed != null ? new FlowTableEvent(TABLE_REMOVED, removed) : null;
    }

    @Override
    public void purgeFlowTable(DeviceId deviceId) {
        flowTable.purgeFlowTable(deviceId);
    }

    @Override
    public void batchOperationComplete(FlowTableBatchEvent event) {
        //FIXME: need a per device pending response
        NodeId nodeId = pendingResponses.remove(event.subject().batchId());
        if (nodeId == null) {
            notifyDelegate(event);
        } else {
            // TODO check unicast return value
            clusterCommunicator.unicast(event, REMOTE_APPLY_COMPLETED, SERIALIZER::encode, nodeId);
            //error log: log.warn("Failed to respond to peer for batch operation result");
        }
    }

    private final class OnStoreBatch implements ClusterMessageHandler {

        @Override
        public void handle(final ClusterMessage message) {
            FlowTableBatchOperation operation = SERIALIZER.decode(message.payload());
            log.debug("received batch request {}", operation);

            final DeviceId deviceId = operation.deviceId();
            NodeId master = mastershipService.getMasterFor(deviceId);
            if (!Objects.equals(local, master)) {
                Set<FlowTable> failures = new HashSet<>(operation.size());
                for (FlowTableBatchEntry op : operation.getOperations()) {
                    failures.add(op.target());
                }
                CompletedTableBatchOperation allFailed = new CompletedTableBatchOperation(false, failures, deviceId);
                // This node is no longer the master, respond as all failed.
                // TODO: we might want to wrap response in envelope
                // to distinguish sw programming failure and hand over
                // it make sense in the latter case to retry immediately.
                message.respond(SERIALIZER.encode(allFailed));
                return;
            }

            pendingResponses.put(operation.id(), message.sender());
            storeBatchInternal(operation);
        }
    }

    private class InternalFlowTable implements ReplicaInfoEventListener {

        private Map<OFTableType,Byte> flowTableNoTmpMap = new HashMap<>();

        private Map<FlowTableId,List<Integer>> freeFlowTableIdListTmpMap = new HashMap<>();


        public int getGlobalFlowTableId(DeviceOFTableType deviceOFTableType) {
            OFTableType ofTableType = deviceOFTableType.getOfTableType();
            DeviceId deviceId = deviceOFTableType.getDeviceId();
            int newFlowTableID = -1;
            if (null == freeFlowTableIdListMap.get(deviceId)
                    || null == freeFlowTableIdListMap.get(deviceId).get(ofTableType)
                    || 0 == freeFlowTableIdListMap.get(deviceId).get(ofTableType).size()) {
                newFlowTableID = flowTableNoMap.get(deviceId).get(ofTableType);
                for(OFTableType ofTableTypeTmp: flowTableNoMap.get(deviceId).keySet()){
                    if(ofTableTypeTmp.equals(ofTableType)){
                        this.flowTableNoTmpMap.put(ofTableTypeTmp, Byte.valueOf((byte)(newFlowTableID + 1)));
                    } else {
                        this.flowTableNoTmpMap.put(ofTableTypeTmp,
                                                   flowTableNoMap.get(deviceId).get(ofTableTypeTmp));
                    }
                }
                flowTableNoMap.put(deviceId, this.flowTableNoTmpMap);
            } else {
                newFlowTableID = freeFlowTableIdListMap.get(deviceId).get(ofTableType).remove(0);
            }

            flowEntryCountTmpMap.putAll(flowEntryCountMap.get(deviceId));
            flowEntryCountTmpMap.put(new FlowTableId(newFlowTableID), 0);
            flowEntryCountMap.put(deviceId, flowEntryCountTmpMap);


            freeFlowTableIdListTmpMap.putAll(freeFlowEntryIdMap.get(deviceId));
            List<Integer> ids = new ArrayList<>();
            this.freeFlowTableIdListTmpMap.put(FlowTableId.valueOf(newFlowTableID), ids);
            freeFlowEntryIdMap.put(deviceId, this.freeFlowTableIdListTmpMap);

            Map<Integer, FlowRule> fs = new ConcurrentHashMap<>();
            flowEntries.get(deviceId).putIfAbsent(FlowTableId.valueOf(newFlowTableID), fs);
            return newFlowTableID;
        }

        public int getFlowEntryId(DeviceTableId deviceTableId) {
            int newFlowEntryId = -1;

            DeviceId deviceId = deviceTableId.getDeviceId();
            int tableId = deviceTableId.getTableId();

            if (null == freeFlowEntryIdMap.get(deviceId)
                    || null == freeFlowEntryIdMap.get(deviceId).get(FlowTableId.valueOf(tableId))
                    || 0 == freeFlowEntryIdMap.get(deviceId)
                    .get(FlowTableId.valueOf(tableId)).size()) {
                newFlowEntryId = getFlowEntryCount(deviceId, FlowTableId.valueOf(tableId));
                addFlowEntryCount(deviceId, FlowTableId.valueOf(tableId));

            } else {
                newFlowEntryId = freeFlowEntryIdMap.get(deviceId)
                        .get(FlowTableId.valueOf(tableId)).remove(0);
                log.info("get new flow table id from freeFlowEntryIDListMap: {}", newFlowEntryId);

            }
            return newFlowEntryId;
        }

        //TODO replace the Map<V,V> with ExtendedSet


        private final Map<DeviceId, Long> lastBackupTimes = Maps.newConcurrentMap();
        private final Map<DeviceId, Long> lastUpdateTimes = Maps.newConcurrentMap();
        private final Map<DeviceId, NodeId> lastBackupNodes = Maps.newConcurrentMap();

        @Override
        public void event(ReplicaInfoEvent event) {
            if (!backupEnabled) {
                return;
            }
            if (event.type() == ReplicaInfoEvent.Type.BACKUPS_CHANGED) {
                DeviceId deviceId = event.subject();
                NodeId master = mastershipService.getMasterFor(deviceId);
                if (!Objects.equals(local, master)) {
                    // ignore since this event is for a device this node does not manage.
                    return;
                }
                NodeId newBackupNode = getBackupNode(deviceId);
                NodeId currentBackupNode = lastBackupNodes.get(deviceId);
                if (Objects.equals(newBackupNode, currentBackupNode)) {
                    // ignore since backup location hasn't changed.
                    return;
                }
                if (currentBackupNode != null && newBackupNode == null) {
                    // Current backup node is most likely down and no alternate backup node
                    // has been chosen. Clear current backup location so that we can resume
                    // backups when either current backup comes online or a different backup node
                    // is chosen.
                    log.warn("Lost backup location {} for deviceId {} and no alternate backup node exists. "
                                     + "Flows can be lost if the master goes down", currentBackupNode, deviceId);
                    lastBackupNodes.remove(deviceId);
                    lastBackupTimes.remove(deviceId);
                    return;
                    // TODO: Pick any available node as backup and ensure hand-off occurs when
                    // a new master is elected.
                }
                log.debug("Backup location for {} has changed from {} to {}.",
                          deviceId, currentBackupNode, newBackupNode);
                backupSenderExecutor.schedule(() -> backupFlowTables(newBackupNode, Sets.newHashSet(deviceId)),
                                              0,
                                              TimeUnit.SECONDS);
            }
        }

        private void sendBackups(NodeId nodeId, Set<DeviceId> deviceIds) {
            // split up the devices into smaller batches and send them separately.
            Iterables.partition(deviceIds, FLOW_TABLE_BACKUP_BATCH_SIZE)
                    .forEach(ids -> backupFlowTables(nodeId, Sets.newHashSet(ids)));
        }

        private void backupFlowTables(NodeId nodeId, Set<DeviceId> deviceIds) {
            if (deviceIds.isEmpty()) {
                return;
            }
            log.debug("Sending flowEntries for devices {} to {} as backup.", deviceIds, nodeId);
            Map<DeviceId, Map<FlowTableId,  StoredFlowTableEntry>>
                    deviceFlowTables = Maps.newConcurrentMap();
            deviceIds.forEach(id -> deviceFlowTables.put(id, ImmutableMap.copyOf(getFlowTables(id))));
            clusterCommunicator.<Map<DeviceId,
                    Map<FlowTableId,  StoredFlowTableEntry>>,
                    Set<DeviceId>>
                    sendAndReceive(deviceFlowTables,
                                   FLOW_TABLE_BACKUP,
                                   SERIALIZER::encode,
                                   SERIALIZER::decode,
                                   nodeId)
                    .whenComplete((backedupDevices, error) -> {
                        Set<DeviceId> devicesNotBackedup = error != null ?
                                deviceFlowTables.keySet() :
                                Sets.difference(deviceFlowTables.keySet(), backedupDevices);
                        if (devicesNotBackedup.size() > 0) {
                            log.warn("Failed to backup devices: {}. Reason: {}",
                                     devicesNotBackedup, error.getMessage());
                        }
                        if (backedupDevices != null) {
                            backedupDevices.forEach(id -> {
                                lastBackupTimes.put(id, System.currentTimeMillis());
                                lastBackupNodes.put(id, nodeId);
                            });
                        }
                    });
        }

        /**
         * Returns the flow table for specified device.
         *
         * @param deviceId identifier of the device
         * @return Map representing Flow Table of given device.
         */
        public Map<FlowTableId,  StoredFlowTableEntry> getFlowTables(DeviceId deviceId) {
            if (persistenceEnabled) {
                return flowTablesMap.computeIfAbsent(deviceId, id -> persistenceService
                        .<FlowTableId,  StoredFlowTableEntry>persistentMapBuilder()
                        .withName("FlowTable:" + deviceId.toString())
                        .withSerializer(new Serializer() {
                            @Override
                            public <T> byte[] encode(T object) {
                                return SERIALIZER.encode(object);
                            }

                            @Override
                            public <T> T decode(byte[] bytes) {
                                return SERIALIZER.decode(bytes);
                            }
                        })
                        .build());
            } else {
                return flowTablesMap.computeIfAbsent(deviceId, id -> Maps.newConcurrentMap());
            }
        }


        private FlowTable getFlowTableInternal(DeviceId deviceId, FlowTableId flowTableId) {
            /*FlowTableId tableId = flowTableId;
            for (FlowTableId id: getFlowTables(deviceId).keySet()) {
                if (flowTableId.value() == id.value()) {
                    tableId = id;
                    //log.info("++++ get flowTable ID: {}", tableId.value());
                    break;
                }
            }*/
            return getFlowTables(deviceId).get(flowTableId);
        }


        public FlowTable getFlowTable(FlowTable table) {
            return getFlowTableInternal(table.deviceId(), table.id());
        }

        public void add(FlowTable table) {
            DeviceId deviceId = table.deviceId();
            FlowTableId flowTableId = table.id();
            flowTablesTmpMap = getFlowTables(deviceId);

            flowTablesTmpMap.putAll(flowTablesMap.get(deviceId));
            flowTablesTmpMap.put(flowTableId,(StoredFlowTableEntry)table);
            flowTablesMap.put(deviceId,flowTablesTmpMap);

            lastUpdateTimes.put(deviceId, System.currentTimeMillis());
        }

        public FlowTable remove(DeviceId deviceId, FlowTable table) {
            DeviceId tableDeviceId = table.deviceId();
            FlowTableId flowTableId = table.id();
            log.info("++++ InternalFlowTable.remove()");
            final AtomicReference<FlowTable> removedRule = new AtomicReference<>();

            log.info("++++ before getFlowTableInternal()");
            FlowTable stored =  getFlowTableInternal(tableDeviceId, flowTableId);
            log.info("++++ after getFlowTableInternal()");

            removedRule.set(stored);

            if (stored != null) {
                log.info("+++++ Remove the table!");
                flowTablesTmpMap.clear();
                flowTablesTmpMap.putAll(flowTablesMap.get(tableDeviceId));
                flowTablesTmpMap.remove(flowTableId);
                flowTablesMap.put(tableDeviceId,flowTablesTmpMap);
                freeFlowTableIdListMap.get(tableDeviceId).get(table.flowTable()
                                                                      .getTableType()).add((byte) flowTableId.value());
                Collections.sort(freeFlowTableIdListMap.get(tableDeviceId)
                                         .get(table.flowTable().getTableType()));
                if (flowEntries.get(tableDeviceId) != null ) {
                    flowEntries.get(table.deviceId()).remove(flowTableId);
                    flowEntryCountTmpMap.clear();
                    flowEntryCountTmpMap.putAll(flowEntryCountMap.get(tableDeviceId));
                    flowEntryCountTmpMap.remove(flowTableId);
                    flowEntryCountMap.put(tableDeviceId,flowEntryCountTmpMap);

                    freeFlowEntryIdTmpMap.clear();
                    freeFlowEntryIdTmpMap.putAll(freeFlowEntryIdMap.get(tableDeviceId));
                    freeFlowEntryIdTmpMap.remove(flowTableId);
                    freeFlowEntryIdMap.put(tableDeviceId,freeFlowEntryIdTmpMap);
                }
            } else {
                log.info("No table exit!");
            }

            if (removedRule.get() != null) {
                lastUpdateTimes.put(deviceId, System.currentTimeMillis());
                return removedRule.get();
            } else {
                return null;
            }
        }

        public void purgeFlowTable(DeviceId deviceId) {
            flowTablesMap.remove(deviceId);
        }

        private NodeId getBackupNode(DeviceId deviceId) {
            List<NodeId> deviceStandbys = replicaInfoManager.getReplicaInfoFor(deviceId).backups();
            // pick the standby which is most likely to become next master
            return deviceStandbys.isEmpty() ? null : deviceStandbys.get(0);
        }

        private void backup() {
            if (!backupEnabled) {
                return;
            }
            try {
                // determine the set of devices that we need to backup during this run.
                Set<DeviceId> devicesToBackup = mastershipService.getDevicesOf(local)
                        .stream()
                        .filter(deviceId -> {
                            Long lastBackupTime = lastBackupTimes.get(deviceId);
                            Long lastUpdateTime = lastUpdateTimes.get(deviceId);
                            NodeId lastBackupNode = lastBackupNodes.get(deviceId);
                            NodeId newBackupNode = getBackupNode(deviceId);
                            return lastBackupTime == null
                                    ||  !Objects.equals(lastBackupNode, newBackupNode)
                                    || (lastUpdateTime != null && lastUpdateTime > lastBackupTime);
                        })
                        .collect(Collectors.toSet());

                // compute a mapping from node to the set of devices whose flow entries it should backup
                Map<NodeId, Set<DeviceId>> devicesToBackupByNode = Maps.newHashMap();
                devicesToBackup.forEach(deviceId -> {
                    NodeId backupLocation = getBackupNode(deviceId);
                    if (backupLocation != null) {
                        devicesToBackupByNode.computeIfAbsent(backupLocation, nodeId -> Sets.newHashSet())
                                .add(deviceId);
                    }
                });
                // send the device flow entries to their respective backup nodes
                devicesToBackupByNode.forEach(this::sendBackups);
            } catch (Exception e) {
                log.error("Backup failed.", e);
            }
        }

        private Set<DeviceId> onBackupReceipt(Map<DeviceId,
                Map<FlowTableId, StoredFlowTableEntry>> flowtables) {

            log.debug("Received flowEntries for {} to backup", flowtables.keySet());
            Set<DeviceId> backedupDevices = Sets.newHashSet();
            try {
                flowtables.forEach((deviceId, deviceFlowTable) -> {
                    // Only process those devices are that not managed by the local node.
                    if (!Objects.equals(local, mastershipService.getMasterFor(deviceId))) {
                        Map<FlowTableId, StoredFlowTableEntry> backupFlowTable =
                                getFlowTables(deviceId);
                        backupFlowTable.clear();
                        backupFlowTable.putAll(deviceFlowTable);
                        backedupDevices.add(deviceId);
                    }
                });
            } catch (Exception e) {
                log.warn("Failure processing backup request", e);
            }
            return backedupDevices;
        }

        public void removeDevice(DeviceId deviceId) {
            log.info("++++ removeDevice");
            flowTablesMap.remove(deviceId);
        }
    }
    private class InternalTableStatsListener
            implements EventuallyConsistentMapListener<DeviceId, List<TableStatisticsEntry>> {
        @Override
        public void event(EventuallyConsistentMapEvent<DeviceId,
                List<TableStatisticsEntry>> event) {
            //TODO: Generate an event to listeners (do we need?)
        }
    }
}
