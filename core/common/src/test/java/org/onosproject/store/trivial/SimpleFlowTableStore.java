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
package org.onosproject.store.trivial;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
//import org.onlab.util.NewConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.onosproject.cluster.NodeId;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.floodlightpof.protocol.table.OFTableResource;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.DeviceId;
import org.onosproject.net.table.DeviceOFTableType;
import org.onosproject.net.table.DeviceTableId;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TableStatisticsEntry;
import org.onosproject.net.table.CompletedTableBatchOperation;
import org.onosproject.net.table.DefaultFlowTableEntry;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableBatchEntry;
import org.onosproject.net.table.FlowTableBatchEvent;
import org.onosproject.net.table.FlowTableBatchOperation;
import org.onosproject.net.table.FlowTableBatchRequest;
import org.onosproject.net.table.FlowTableEntry;
import org.onosproject.net.table.FlowTableEntry.FlowTableState;
import org.onosproject.net.table.FlowTableEvent;
import org.onosproject.net.table.FlowTableId;
import org.onosproject.net.table.FlowTableStore;
import org.onosproject.net.table.FlowTableStoreDelegate;
import org.onosproject.net.table.StoredFlowTableEntry;
import org.onosproject.store.AbstractStore;
import org.onosproject.store.service.EventuallyConsistentMapEvent;
import org.onosproject.store.service.EventuallyConsistentMapListener;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.lang3.concurrent.ConcurrentUtils.createIfAbsentUnchecked;
import static org.onosproject.net.table.FlowTableEvent.Type.TABLE_REMOVED;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Manages inventory of flow rules using a distributed state management protocol.
 * created by sqy
 */
@Component(immediate = true)
@Service
public class SimpleFlowTableStore
        extends AbstractStore<FlowTableBatchEvent, FlowTableStoreDelegate>
        implements FlowTableStore {

    private final Logger log = getLogger(getClass());

    private static final int MESSAGE_HANDLER_THREAD_POOL_SIZE = 8;
    private static final boolean DEFAULT_BACKUP_ENABLED = true;
    private static final int DEFAULT_BACKUP_PERIOD_MILLIS = 2000;

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

    private static Map<DeviceId, Map<FlowTableId, List<Integer>>>
            freeFlowEntryIds = Maps.newConcurrentMap();
    private static Map<DeviceId, Map<FlowTableId, Integer>>
            flowEntryCount = Maps.newConcurrentMap();

    private static Map<DeviceId, Map<OFTableType, Byte>>
            flowTableNoBaseMap = Maps.newConcurrentMap();    //<tableType, NumberBase>

    private static Map<DeviceId, Map<OFTableType, Byte>>
            flowTableNoMap = Maps.newConcurrentMap();        //<tableType, globalTableId>

    private static Map<DeviceId, Map<OFTableType, List<Byte>>>
            freeFlowTableIDListMap = Maps.newConcurrentMap();

    private static Map<DeviceId, Map<FlowTableId, Map<Integer, FlowRule>>>
            flowEntries = Maps.newConcurrentMap();
//
//    private final ConcurrentMap<DeviceId, ConcurrentMap<FlowTableId, ConcurrentMap<Integer, FlowRule>>>
//            flowEntries = Maps.newConcurrentMap();

    private final ConcurrentMap<DeviceId, ConcurrentMap<FlowTableId, List<StoredFlowTableEntry>>>
            flowTables = new ConcurrentHashMap<>();

//    static private final Map<DeviceId, Map<FlowTableId,  StoredFlowTableEntry>>
//            flowTables = Maps.newConcurrentMap();


    private Map<Long, NodeId> pendingResponses = Maps.newConcurrentMap();

    private final ConcurrentMap<DeviceId, List<TableStatisticsEntry>>
            deviceTableStats = new ConcurrentHashMap<>();


    private final AtomicInteger localBatchIdGen = new AtomicInteger();
    private int pendingFutureTimeoutMinutes = 5;

    private Cache<Integer, SettableFuture<CompletedTableBatchOperation>> pendingFutures =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(pendingFutureTimeoutMinutes, TimeUnit.MINUTES)
                    .removalListener(new TimeoutFuture())
                    .build();

    @Activate
    public void activate() {
        logConfig("Started");
    }

    @Deactivate
    public void deactivate() {
//        if (backupEnabled) {
//            backupTask.cancel(true);
//        }
//        deviceTableStats.removeListener(tableStatsListener);
//        deviceTableStats.destroy();
//        messageHandlingExecutor.shutdownNow();
//        backupSenderExecutor.shutdownNow();
        deviceTableStats.clear();
        flowTables.clear();
        log.info("Stopped");
    }

    private void logConfig(String prefix) {
        log.info("{} with msgHandlerPoolSize = {}; backupEnabled = {}, backupPeriod = {}",
                prefix, msgHandlerPoolSize, backupEnabled, backupPeriod);
    }

    /**
     * Initialize flowtables and flowentries store.
     */
    @Override
    public void initializeSwitchStore(DeviceId deviceId) {

        log.info("initializeSwitchStore for device: {}", deviceId);
        Map<FlowTableId, Integer> tcount = new ConcurrentHashMap<>();
        flowEntryCount.putIfAbsent(deviceId, tcount);

        List<Integer> ids = new ArrayList<>();
        Map<FlowTableId, List<Integer>> tids = new ConcurrentHashMap<>();
        freeFlowEntryIds.putIfAbsent(deviceId, tids);

        Map<Integer, FlowRule> fs = new ConcurrentHashMap<>();
        Map<FlowTableId, Map<Integer, FlowRule>> tfs = new ConcurrentHashMap<>();
        flowEntries.putIfAbsent(deviceId, tfs);
    }


    @Override
    public void removeSwitchStore(DeviceId deviceId) {

        log.info("removeSwitchStore for device: {}", deviceId);
        flowEntryCount.remove(deviceId);
        freeFlowEntryIds.remove(deviceId);
        flowEntries.remove(deviceId);

        freeFlowTableIDListMap.remove(deviceId);
        flowTableNoMap.remove(deviceId);
        flowTableNoBaseMap.remove(deviceId);
        flowTables.remove(deviceId);
    }


    // This is not a efficient operation on a distributed sharded
    // flow store. We need to revisit the need for this operation or at least
    // make it device specific.
    @Override
    public int getFlowTableCount() {
        AtomicInteger sum = new AtomicInteger(0);
        return sum.get();
    }

//    private static ConcurrentMap<FlowTableId, List<StoredFlowTableEntry>> lazyEmptyFlowTable() {
//        return ConcurrenthMap.<FlowTableId, List<StoredFlowTableEntry>>ifNeeded();
//    }

    /**
     * Returns the flow table for specified device.
     *
     * @param deviceId identifier of the device
     * @return Map representing Flow Table of given device.
     */
    private ConcurrentMap<FlowTableId, List<StoredFlowTableEntry>> getFlowTable(DeviceId deviceId) {
//        return createIfAbsentUnchecked(flowTables,
//                deviceId, lazyEmptyFlowTable());
        return flowTables.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>());
    }

    private List<StoredFlowTableEntry> getFlowTables(DeviceId deviceId, FlowTableId flowId) {
        final ConcurrentMap<FlowTableId, List<StoredFlowTableEntry>> flowTable = getFlowTable(deviceId);
        List<StoredFlowTableEntry> r = flowTable.get(flowId);
        if (r == null) {
            final List<StoredFlowTableEntry> concurrentlyAdded;
            r = new CopyOnWriteArrayList<>();
            concurrentlyAdded = flowTable.putIfAbsent(flowId, r);
            if (concurrentlyAdded != null) {
                return concurrentlyAdded;
            }
        }
        return r;
    }

//    private FlowTableEntry getFlowTableEntryInternal(DeviceId deviceId, FlowTable rule) {
//        List<StoredFlowTableEntry> fes = getFlowTables(deviceId, rule.id());
//        for (StoredFlowTableEntry fe : fes) {
//            if (fe.equals(rule)) {
//                return fe;
//            }
//        }
//        return null;
//    }


    @Override
    public void setFlowTableNoBase(DeviceId deviceId, OFFlowTableResource of) {
        log.info("+++++ setFlowTableNoBase for device {}", deviceId.toString());
        byte base = 0;
        OFTableResource tableResource;
        Map<OFTableType, OFTableResource> flowTableReourceMap = of.getTableResourcesMap();

        Map<OFTableType, Byte> noMap = new ConcurrentHashMap<>();
        Map<OFTableType, Byte> noBaseMap = new ConcurrentHashMap<>();
        Map<OFTableType, List<Byte>> freeIDListMap = new ConcurrentHashMap<>();

        try {
            if (flowTableReourceMap == null) {
                return;
            }
            for (byte tableType = 0; tableType < OFTableType.OF_MAX_TABLE_TYPE.getValue(); tableType++) {
                tableResource = flowTableReourceMap.get(OFTableType.values()[tableType]);
                noMap.put(OFTableType.values()[tableType], base);
                noBaseMap.put(OFTableType.values()[tableType], base);
                freeIDListMap.put(OFTableType.values()[tableType], new ArrayList<Byte>());
                base += tableResource.getTableNum();
            }

            this.flowTableNoMap.put(deviceId, noMap);
            this.flowTableNoBaseMap.put(deviceId, noBaseMap);
            this.freeFlowTableIDListMap.put(deviceId, freeIDListMap);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    @Override
    public int getNewGlobalFlowTableId(DeviceId deviceId, OFTableType tableType) {
        int newFlowTableID = -1;

        try {
            OFTableType ofTableType = tableType;
            if (null == freeFlowTableIDListMap.get(deviceId)
                    || null == freeFlowTableIDListMap.get(deviceId).get(ofTableType)
                    || 0 == freeFlowTableIDListMap.get(deviceId).get(ofTableType).size()) {

                newFlowTableID = flowTableNoMap.get(deviceId).get(ofTableType);
                flowTableNoMap.get(deviceId).replace(ofTableType, Byte.valueOf((byte) (newFlowTableID + 1)));
                log.info("get new flow table id from flowTableNoMap: {}", newFlowTableID);
            } else {
                newFlowTableID = freeFlowTableIDListMap.get(deviceId).get(ofTableType).remove(0);
                log.info("get new flow table id from freeFlowTableIDListMap: {}", newFlowTableID);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        flowEntryCount.get(deviceId).putIfAbsent(FlowTableId.valueOf(newFlowTableID), 0);
        List<Integer> ids = new ArrayList<>();
        freeFlowEntryIds.get(deviceId).putIfAbsent(FlowTableId.valueOf(newFlowTableID), ids);
        Map<Integer, FlowRule> fs = new ConcurrentHashMap<>();
        flowEntries.get(deviceId).putIfAbsent(FlowTableId.valueOf(newFlowTableID), fs);

        return newFlowTableID;
    }

    public int getGlobalTableId(DeviceOFTableType deviceOFTableType) {
        int newFlowTableID = -1;
        DeviceId deviceId = deviceOFTableType.getDeviceId();
        OFTableType ofTableType = deviceOFTableType.getOfTableType();
        try {

            if (null == freeFlowTableIDListMap.get(deviceId)
                    || null == freeFlowTableIDListMap.get(deviceId).get(ofTableType)
                    || 0 == freeFlowTableIDListMap.get(deviceId).get(ofTableType).size()) {

                newFlowTableID = flowTableNoMap.get(deviceId).get(ofTableType);
                flowTableNoMap.get(deviceId).replace(ofTableType, Byte.valueOf((byte) (newFlowTableID + 1)));
                log.info("get new flow table id from flowTableNoMap: {}", newFlowTableID);
            } else {
                newFlowTableID = freeFlowTableIDListMap.get(deviceId).get(ofTableType).remove(0);
                log.info("get new flow table id from freeFlowTableIDListMap: {}", newFlowTableID);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        flowEntryCount.get(deviceId).putIfAbsent(FlowTableId.valueOf(newFlowTableID), 0);
        List<Integer> ids = new ArrayList<>();
        freeFlowEntryIds.get(deviceId).putIfAbsent(FlowTableId.valueOf(newFlowTableID), ids);
        Map<Integer, FlowRule> fs = new ConcurrentHashMap<>();
        flowEntries.get(deviceId).putIfAbsent(FlowTableId.valueOf(newFlowTableID), fs);

        return newFlowTableID;
    }

    @Override
    public byte parseToSmallTableId(DeviceId deviceId, int globalTableId) {
        FlowTableId tableId = FlowTableId.valueOf(globalTableId);

        if (flowTables.get(deviceId) != null) {
            FlowTable flowTable = (FlowTable) flowTables.get(deviceId).get(tableId);
            if (flowTable != null) {
                return flowTable.flowTable().getTableId();
            }
        }

        for (byte tableType = (byte) (OFTableType.OF_MAX_TABLE_TYPE.getValue() - 1); tableType >= 0; tableType--) {
            byte flowTableNoBase = flowTableNoBaseMap.get(deviceId).get(OFTableType.values()[tableType]);

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
    public int getNewFlowEntryId(DeviceId deviceId, int tableId) {
        int newFlowEntryId = -1;
        log.info("++++ getNewFlowEntryId1");
        try {
            if (null == freeFlowEntryIds.get(deviceId)
                    || null == freeFlowEntryIds.get(deviceId).get(FlowTableId.valueOf(tableId))
                    || 0 == freeFlowEntryIds.get(deviceId).get(FlowTableId.valueOf(tableId)).size()) {

                log.info("++++ getNewFlowEntryId2");
                newFlowEntryId = getFlowEntryCount(deviceId, FlowTableId.valueOf(tableId));
                addFlowEntryCount(deviceId, FlowTableId.valueOf(tableId));
                log.info("get new flow table id from flowEntryCount: {}", newFlowEntryId);
                int tempNext = getFlowEntryCount(deviceId, FlowTableId.valueOf(tableId));
                log.info("temp_next:{}", tempNext);
            } else {
                log.info("++++ getNewFlowEntryId3");
                newFlowEntryId = freeFlowEntryIds.get(deviceId).get(FlowTableId.valueOf(tableId)).remove(0);
                log.info("get new flow table id from freeFlowEntryIDListMap: {}", newFlowEntryId);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return newFlowEntryId;
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
        return this.freeFlowTableIDListMap.get(deviceId);
    }

    @Override
    public int getFlowEntryCount(DeviceId deviceId, FlowTableId flowTableId) {
        return flowEntryCount.get(deviceId).get(flowTableId);
    }

    @Override
    public void addFlowEntryCount(DeviceId deviceId, FlowTableId flowTableId) {
        Integer tmp = flowEntryCount.get(deviceId).get(flowTableId);
        flowEntryCount.get(deviceId).replace(flowTableId, tmp + 1);
    }

    @Override
    public void deleteFlowEntryCount(DeviceId deviceId, FlowTableId flowTableId) {
        Integer tmp = flowEntryCount.get(deviceId).get(flowTableId);
        flowEntryCount.get(deviceId).replace(flowTableId, tmp - 1);
    }

    @Override
    public List<Integer> getFreeFlowEntryIds(DeviceId deviceId, FlowTableId flowTableId) {
        return freeFlowEntryIds.get(deviceId).get(flowTableId);
    }

    @Override
    public void addFreeFlowEntryIds(DeviceId deviceId, FlowTableId flowTableId, Integer flowEntryId) {
        freeFlowEntryIds.get(deviceId).get(flowTableId).add(flowEntryId);

    }

    @Override
    public void deleteFreeFlowEntryIds(DeviceId deviceId, FlowTableId flowTableId, Integer flowEntryId) {
        freeFlowEntryIds.get(deviceId).get(flowTableId).remove((Object) flowEntryId);
    }

    public Map<FlowTableId, Map<Integer, FlowRule>> getFlowEntries(DeviceId deviceId) {
        log.info("+++++ getFlowEntries by deviceId: {}", deviceId);
        if (flowEntries.get(deviceId) == null) {
            log.info("++++ no table in deviceId: {}", deviceId);
            return null;
        } else {
            return flowEntries.get(deviceId);
        }
    }

    @Override
    public Map<Integer, FlowRule> getFlowEntries(DeviceId deviceId, FlowTableId flowTableId) {
        log.info("+++++ getFlowEntries flowentries:{}", flowEntries.toString());
        for (FlowTableId id : flowEntries.get(deviceId).keySet()) {
            if (flowTableId.value() == id.value()) {
                log.info("+++++ get getFlowEntries, get table id");
                flowTableId = id;
            }
        }
        if (flowEntries.get(deviceId).get(flowTableId) == null) {
            log.info("+++++ getFlowEntries by tableID: {} is null", flowTableId);
        }
        return getFlowEntries(deviceId).get(flowTableId);
    }

//    @Override
//    public void addFlowEntry(DeviceId deviceId, FlowTableId flowTableId, OFFlowMod ofFlowMod) {
//        flowEntries.get(deviceId).get(flowTableId).put(ofFlowMod.getIndex(), ofFlowMod);
//    }

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
//        List<StoredFlowEntry> fes = getFlowEntries(deviceId, rule.id());
//        for (StoredFlowEntry fe : fes) {
//            if (fe.equals(rule)) {
//                return fe;
//            }
//        }
        return null;
//        return flowTable.getFlowTableInternal(deviceId, flowTableId);
    }

    private FlowTable getFlowTableInternal(DeviceId deviceId, FlowTable flowTable) {
        List<StoredFlowTableEntry> fes = getFlowTables(deviceId, flowTable.id());
        for (StoredFlowTableEntry fe : fes) {
            if (fe.equals(flowTable)) {
                return fe;
            }
        }
        return null;
    }

    @Override
    public FlowTable getFlowTable(FlowTable table) {
        return getFlowTableInternal(table.deviceId(), table);
    }

    @Override
    public Iterable<FlowTable> getFlowTables(DeviceId deviceId) {

        return FluentIterable.from(getFlowTable(deviceId).values())
                .transformAndConcat(Collections::unmodifiableList);
    }


    @Override
    public void storeFlowTable(FlowTable rule) {
        storeFlowTableInternal(rule);
    }

    private void storeFlowTableInternal(FlowTable rule) {
        StoredFlowTableEntry f = new DefaultFlowTableEntry(rule);
        final DeviceId did = f.deviceId();
        final FlowTableId fid = f.id();
        List<StoredFlowTableEntry> existing = getFlowTables(did, fid);
        synchronized (existing) {
            for (StoredFlowTableEntry fe : existing) {
                if (fe.equals(rule)) {
                    // was already there? ignore
                    return;
                }
            }
            // new flow rule added
            existing.add(f);
        }
    }


    @Override
    public void storeBatch(
            FlowTableBatchOperation operation) {
        List<FlowTableBatchEntry> toAdd = new ArrayList<>();
        List<FlowTableBatchEntry> toRemove = new ArrayList<>();


        for (FlowTableBatchEntry entry : operation.getOperations()) {
            final FlowTable flowTable = entry.target();
            if (entry.operator().equals(FlowTableBatchEntry.FlowTableOperation.ADD)) {
                if (!getFlowTables(flowTable.deviceId(), flowTable.id()).equals(flowTable)) {
                    storeFlowTable(flowTable);
                    log.info("++++sqy SimpleFlowTableStore.storeBatch ADD entry:{}", entry.target().toString());
                    toAdd.add(entry);
                }
            } else if (entry.operator().equals(FlowTableBatchEntry.FlowTableOperation.REMOVE)) {
                if (getFlowTables(flowTable.deviceId(), flowTable.id()).contains(flowTable)) {
                    deleteFlowTable(flowTable);
                    toRemove.add(entry);
                }
            } else {
                throw new UnsupportedOperationException("Unsupported operation type");
            }
        }

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            notifyDelegate(FlowTableBatchEvent.completed(
                    new FlowTableBatchRequest(operation.id(), Collections.emptySet()),
                    new CompletedTableBatchOperation(true, Collections.emptySet(),
                            operation.deviceId())));
            return;
        }

        SettableFuture<CompletedTableBatchOperation> r = SettableFuture.create();
        final int batchId = localBatchIdGen.incrementAndGet();

        pendingFutures.put(batchId, r);

        toAdd.addAll(toRemove);
        notifyDelegate(FlowTableBatchEvent.requested(
                new FlowTableBatchRequest(batchId, Sets.newHashSet(toAdd)), operation.deviceId()));

    }


    @Override
    public void deleteFlowTable(FlowTable table) {
        List<StoredFlowTableEntry> entries = getFlowTables(table.deviceId(), table.id());

        synchronized (entries) {
            for (StoredFlowTableEntry entry : entries) {
                if (entry.equals(table)) {
                    synchronized (entry) {
                        entry.setState(FlowTableState.PENDING_REMOVE);
                    }
                }
            }
        }
    }

    @Override
    public FlowTableEvent pendingFlowTable(FlowTable table) {

        List<StoredFlowTableEntry> entries = getFlowTables(table.deviceId(), table.id());
        synchronized (entries) {
            for (StoredFlowTableEntry entry : entries) {
                if (entry.equals(table) &&
                        entry.state() != FlowTableState.PENDING_ADD) {
                    synchronized (entry) {
                        entry.setState(FlowTableState.PENDING_ADD);
                        return new FlowTableEvent(FlowTableEvent.Type.TABLE_UPDATED, table);
                    }
                }
            }
        }
        return null;
    }


    @Override
    public FlowTableEvent addOrUpdateFlowTable(FlowTable rule) {
        // check if this new rule is an update to an existing entry
        List<StoredFlowTableEntry> entries = getFlowTables(rule.deviceId(), rule.id());
        FlowTableEntry flowTableEntry = (FlowTableEntry) rule;
        synchronized (entries) {
            for (StoredFlowTableEntry stored : entries) {
                if (stored.equals(rule)) {
                    synchronized (stored) {
                        //FIXME modification of "stored" flow entry outside of flow table
                        stored.setBytes(flowTableEntry.bytes());
                        stored.setLife(flowTableEntry.life());
                        stored.setPackets(flowTableEntry.packets());
                        if (stored.state() == FlowTableState.PENDING_ADD) {
                            stored.setState(FlowTableState.ADDED);
                            // TODO: Do we need to change `rule` state?
                            return new FlowTableEvent(FlowTableEvent.Type.TABLE_ADDED, rule);
                        }
                        return new FlowTableEvent(FlowTableEvent.Type.TABLE_UPDATED, rule);
                    }
                }
            }
        }

        // should not reach here
        // storeFlowRule was expected to be called
        log.error("FlowRule was not found in store {} to update", rule);

        //flowEntries.put(did, rule);
        return null;
    }


    @Override
    public FlowTableEvent removeFlowTable(FlowTable rule) {
        // This is where one could mark a rule as removed and still keep it in the store.
        final DeviceId did = rule.deviceId();

        List<StoredFlowTableEntry> entries = getFlowTables(did, rule.id());
        synchronized (entries) {
            if (entries.remove(rule)) {
                return new FlowTableEvent(TABLE_REMOVED, rule);
            }
        }
        return null;
    }

    @Override
    public void purgeFlowTable(DeviceId deviceId) {
        flowTables.remove(deviceId);
    }

    @Override
    public void batchOperationComplete(FlowTableBatchEvent event) {
        //FIXME: need a per device pending response
        NodeId nodeId = pendingResponses.remove(event.subject().batchId());
        if (nodeId == null) {
            notifyDelegate(event);
        } else {
            // TODO check unicast return value
            //clusterCommunicator.unicast(event, REMOTE_APPLY_COMPLETED, SERIALIZER::encode, nodeId);
            //error log: log.warn("Failed to respond to peer for batch operation result");
        }
    }

    private static final class TimeoutFuture
            implements RemovalListener<Integer, SettableFuture<CompletedTableBatchOperation>> {
        @Override
        public void onRemoval(RemovalNotification<Integer, SettableFuture<CompletedTableBatchOperation>> notification) {
            // wrapping in ExecutionException to support Future.get
            if (notification.wasEvicted()) {
                notification.getValue()
                        .setException(new ExecutionException("Timed out",
                                new TimeoutException()));
            }
        }
    }

//    private final class OnStoreBatch implements ClusterMessageHandler {
//
//        @Override
//        public void handle(final ClusterMessage message) {
//            FlowTableBatchOperation operation = SERIALIZER.decode(message.payload());
//            log.debug("received batch request {}", operation);
//
//            final DeviceId deviceId = operation.deviceId();
//            Set<FlowTable> failures = new HashSet<>(operation.size());
//            for (FlowTableBatchEntry op : operation.getOperations()) {
//                failures.add(op.target());
//            }
//            CompletedTableBatchOperation allFailed = new CompletedTableBatchOperation(false, failures, deviceId);
//                // This node is no longer the master, respond as all failed.
//                // TODO: we might want to wrap response in envelope
//                // to distinguish sw programming failure and hand over
//                // it make sense in the latter case to retry immediately.
//            message.respond(SERIALIZER.encode(allFailed));
//            return;
//
//            pendingResponses.put(operation.id(), message.sender());
//            storeBatchInternal(operation);
//        }
//    }


//    @Override
//    public FlowTableEvent updateTableStatistics(DeviceId deviceId,
//                                               List<TableStatisticsEntry> tableStats) {
//        deviceTableStats.put(deviceId, tableStats);
//        return null;
//    }

//    @Override
//    public Iterable<TableStatisticsEntry> getTableStatistics(DeviceId deviceId) {
//        NodeId master = mastershipService.getMasterFor(deviceId);
//
//        if (master == null) {
//            log.debug("Failed to getTableStats: No master for {}", deviceId);
//            return Collections.emptyList();
//        }
//
//        List<TableStatisticsEntry> tableStats = deviceTableStats.get(deviceId);
//        if (tableStats == null) {
//            return Collections.emptyList();
//        }
//        return ImmutableList.copyOf(tableStats);
//    }

    private class InternalTableStatsListener
            implements EventuallyConsistentMapListener<DeviceId, List<TableStatisticsEntry>> {
        @Override
        public void event(EventuallyConsistentMapEvent<DeviceId,
                List<TableStatisticsEntry>> event) {
            //TODO: Generate an event to listeners (do we need?)
        }
    }
}