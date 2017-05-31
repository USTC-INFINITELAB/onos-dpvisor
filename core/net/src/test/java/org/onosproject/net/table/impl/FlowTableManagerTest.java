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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onosproject.cfg.ComponentConfigAdapter;
import org.onosproject.common.event.impl.TestEventDispatcher;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.core.IdGenerator;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.mastership.MastershipServiceAdapter;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.MastershipRole;
import org.onosproject.net.device.DeviceServiceAdapter;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.driver.DefaultDriver;
import org.onosproject.net.driver.impl.DriverManager;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableEntry;
import org.onosproject.net.table.DefaultFlowTableEntry;
import org.onosproject.net.table.FlowTableService;
import org.onosproject.net.table.FlowTableProviderRegistry;
import org.onosproject.net.table.FlowTableProviderService;
import org.onosproject.net.table.FlowTableProgrammable;
import org.onosproject.net.table.DefaultFlowTable;
import org.onosproject.net.table.FlowTableEvent;
import org.onosproject.net.table.FlowTableListener;
import org.onosproject.net.table.FlowTableProvider;
import org.onosproject.net.table.CompletedTableBatchOperation;
import org.onosproject.net.table.FlowTableBatchOperation;
import org.onosproject.net.table.StoredFlowTableEntry;
import org.onosproject.net.table.FlowTableEntry.FlowTableState;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.store.trivial.SimpleFlowTableStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.onosproject.net.NetTestTools.injectEventDispatcher;
import static org.onosproject.net.table.FlowTableEvent.Type.*;

import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Test codifying the flow table service & flow table provider service contracts.
 */
public class FlowTableManagerTest {
    private static final Logger log = getLogger(FlowTableManagerTest.class);

    private static final ProviderId PID = new ProviderId("pof", "foo");
    private static final ProviderId FOO_PID = new ProviderId("foo", "foo");

    private static final DeviceId DID = DeviceId.deviceId("pof:ffffffff84088aad");
    private static final DeviceId FOO_DID = DeviceId.deviceId("foo:002");

    private static final DefaultAnnotations ANNOTATIONS =
            DefaultAnnotations.builder().set(AnnotationKeys.DRIVER, "foo").build();

    private static final Device DEV =
            new DefaultDevice(PID, DID, Device.Type.SWITCH, "", "", "", "", null);
    private static final Device FOO_DEV =
            new DefaultDevice(FOO_PID, FOO_DID, Device.Type.SWITCH, "", "", "", "", null, ANNOTATIONS);

    private FlowTableManager mgr;

    protected FlowTableService service;
    protected FlowTableProviderRegistry registry;
    protected FlowTableProviderService providerService;
    protected TestProvider provider;
    protected TestListener listener = new TestListener();
    private ApplicationId appId;

    private TestDriverManager driverService;


    @Before
    public void setUp() {
        mgr = new FlowTableManager();
        mgr.store = new SimpleFlowTableStore();
        //mgr.store = store;
        mgr.store.initializeSwitchStore(DID);
        mgr.store.initializeSwitchStore(FOO_DID);
        injectEventDispatcher(mgr, new TestEventDispatcher());
        mgr.deviceService = new TestDeviceService();
        mgr.mastershipService = new TestMastershipService();
        mgr.coreService = new TestCoreService();
        mgr.operationsService = MoreExecutors.newDirectExecutorService();
        mgr.deviceInstallers = MoreExecutors.newDirectExecutorService();
        mgr.cfgService = new ComponentConfigAdapter();
        service = mgr;
        registry = mgr;

        driverService = new TestDriverManager();
        mgr.activate(null);
        mgr.addListener(listener);
        provider = new TestProvider(PID);
        providerService = registry.register(provider);
        appId = new TestApplicationId(0, "FlowTableManagerTest");
        assertTrue("provider should be registered",
                registry.getProviders().contains(provider.id()));
    }

    @After
    public void tearDown() {
        registry.unregister(provider);
        assertFalse("provider should not be registered",
                registry.getProviders().contains(provider.id()));
        service.removeListener(listener);
        mgr.deactivate();
        injectEventDispatcher(mgr, null);
        mgr.deviceService = null;
    }

    private FlowTable flowTable(byte globalTableId, byte smallTableId) {
        return flowTable(DID, globalTableId, smallTableId);
    }

    private FlowTable flowTable(DeviceId did, byte globalTableId, byte smallTableId) {

        OFMatch20 ofMatch20 = new OFMatch20();
        ofMatch20.setFieldId((short) 1);
        ofMatch20.setFieldName("test");
        ofMatch20.setOffset((short) 0);
        ofMatch20.setLength((short) 48);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        match20List.add(ofMatch20);

        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId(smallTableId);
        ofFlowTable.setTableName("FirstEntryTable");
        ofFlowTable.setTableSize(128);
        ofFlowTable.setTableType(OFTableType.OF_MM_TABLE);
        ofFlowTable.setMatchFieldList(match20List);

        return DefaultFlowTable.builder()
                .withFlowTable(ofFlowTable)
                .forTable(globalTableId)
                .forDevice(did)
                .fromApp(appId)
                .build();
    }

    private FlowTable addFlowTable(byte hval) {
        FlowTable rule = flowTable(hval, hval);
        service.applyFlowTables(rule);

        assertNotNull("rule should be found", service.getFlowTables(DID));
        return rule;
    }

    private void validateEvents(FlowTableEvent.Type... events) {
        if (events == null) {
            assertTrue("events generated", listener.events.isEmpty());
        }

        int i = 0;
        System.err.println("events :" + listener.events);
        for (FlowTableEvent e : listener.events) {
            assertEquals("unexpected event", events[i], e.type());
            i++;
        }

        assertEquals("mispredicted number of events",
                events.length, listener.events.size());

        listener.events.clear();
    }

    private int tableCount() {
        return Sets.newHashSet(service.getFlowTables(DID)).size();
    }

//    @Test
//    public void getFlowTables() {
//
//        log.info("++++sqy service:{},{}", service.toString(),DID.toString());
//        log.info("++++sqy service.getFlowTables(DID):{}");
//        assertTrue("store should be empty",
//                Sets.newHashSet(service.getFlowTables(DID)).isEmpty());
//        FlowTable f1 = addFlowTable((byte)0);
//        FlowTable f2 = addFlowTable((byte) 1);
//
//        FlowTableEntry fe1 = new DefaultFlowTableEntry(f1);
//        FlowTableEntry fe2 = new DefaultFlowTableEntry(f2);
//        assertEquals("2 tables should exist", 2, tableCount());
//
//        providerService.pushTableMetrics(DID, ImmutableList.of(fe1, fe2));
//        validateEvents(TABLE_ADD_REQUESTED, TABLE_ADD_REQUESTED,
//                TABLE_ADDED, TABLE_ADDED);
//
//        addFlowTable((byte)0);
//        System.err.println("events :" + listener.events);
//        assertEquals("should still be 2 tables", 2, tableCount());
//
//        providerService.pushTableMetrics(DID, ImmutableList.of(fe1));
//        validateEvents(TABLE_UPDATED, TABLE_UPDATED);
//    }

    private boolean validateState(Map<FlowTable, FlowTableState> expected) {
        Map<FlowTable, FlowTableState> expectedToCheck = new HashMap<>(expected);
        Iterable<FlowTable> rules = service.getFlowTables(DID);
        for (FlowTable f : rules) {
            assertTrue("Unexpected FlowTable " + f, expectedToCheck.containsKey(f));
            //assertEquals("FlowTableEntry" + f, expectedToCheck.get(f), f.state());
            expectedToCheck.remove(f);
        }
        assertEquals(Collections.emptySet(), expectedToCheck.entrySet());
        return true;
    }


    @Test
    public void applyFlowTables() {

        FlowTable r1 = flowTable((byte) 0, (byte) 0);
        FlowTable r2 = flowTable((byte) 1, (byte) 1);
        FlowTable r3 = flowTable((byte) 2, (byte) 2);

        mgr.applyFlowTables(r1, r2, r3);
        assertEquals("3 tables should exist", 3, tableCount());
        assertTrue("Entries should be pending add.",
                validateState(ImmutableMap.of(
                        r1, FlowTableState.PENDING_ADD,
                        r2, FlowTableState.PENDING_ADD,
                        r3, FlowTableState.PENDING_ADD)));
    }

    @Test
    public void removeFlowTables() {
        FlowTable f1 = addFlowTable((byte) 0);
        FlowTable f2 = addFlowTable((byte) 1);
        FlowTable f3 = addFlowTable((byte) 2);
        assertEquals("3 rules should exist", 3, tableCount());

        FlowTableEntry fe1 = new DefaultFlowTableEntry(f1);
        FlowTableEntry fe2 = new DefaultFlowTableEntry(f2);
        FlowTableEntry fe3 = new DefaultFlowTableEntry(f3);
        providerService.pushTableMetrics(DID, ImmutableList.of(fe1, fe2, fe3));
        validateEvents(TABLE_ADD_REQUESTED, TABLE_ADD_REQUESTED, TABLE_ADD_REQUESTED,
                TABLE_ADDED, TABLE_ADDED, TABLE_ADDED);

        mgr.removeFlowTables(f1, f2);
        //removing from north, so no events generated
        validateEvents(TABLE_REMOVE_REQUESTED, TABLE_REMOVE_REQUESTED);
        assertEquals("3 rule should exist", 3, tableCount());
        assertTrue("Entries should be pending remove.",
                validateState(ImmutableMap.of(
                        f1, FlowTableState.PENDING_REMOVE,
                        f2, FlowTableState.PENDING_REMOVE,
                        f3, FlowTableState.ADDED)));

        mgr.removeFlowTables(f1);
        assertEquals("3 rule should still exist", 3, tableCount());
    }

    @Test
    public void tableRemoved() {
        FlowTable f1 = addFlowTable((byte) 0);
        FlowTable f2 = addFlowTable((byte) 1);
        StoredFlowTableEntry fe1 = new DefaultFlowTableEntry(f1);
        FlowTableEntry fe2 = new DefaultFlowTableEntry(f2);

        providerService.pushTableMetrics(DID, ImmutableList.of(fe1, fe2));
        service.removeFlowTables(f1);

        //FIXME modification of "stored" flow entry outside of store
        fe1.setState(FlowTableState.REMOVED);

        providerService.flowTableRemoved(fe1);

        validateEvents(TABLE_ADD_REQUESTED, TABLE_ADD_REQUESTED, TABLE_ADDED,
                TABLE_ADDED, TABLE_REMOVE_REQUESTED, TABLE_REMOVED);

        providerService.flowTableRemoved(fe1);
        validateEvents();

        FlowTable f3 = flowTable((byte) 2, (byte) 2);
        FlowTableEntry fe3 = new DefaultFlowTableEntry(f3);
        service.applyFlowTables(f3);

        providerService.pushTableMetrics(DID, Collections.singletonList(fe3));
        validateEvents(TABLE_ADD_REQUESTED, TABLE_ADDED, TABLE_UPDATED);

        providerService.flowTableRemoved(fe3);
        validateEvents();
    }

    @Test
    public void tableMetrics() {
        FlowTable f1 = flowTable((byte) 0, (byte) 0);
        FlowTable f2 = flowTable((byte) 1, (byte) 1);
        FlowTable f3 = flowTable((byte) 2, (byte) 2);

        mgr.applyFlowTables(f1, f2, f3);

        FlowTableEntry fe1 = new DefaultFlowTableEntry(f1);
        FlowTableEntry fe2 = new DefaultFlowTableEntry(f2);

        //FlowTable updatedF1 = flowTable(f1, FlowTableState.ADDED);
        //FlowTable updatedF2 = flowTable(f2, FlowTableState.ADDED);

        providerService.pushTableMetrics(DID, Lists.newArrayList(fe1, fe2));

        assertTrue("Entries should be added.",
                validateState(ImmutableMap.of(
                        f1, FlowTableState.ADDED,
                        f2, FlowTableState.ADDED,
                        f3, FlowTableState.PENDING_ADD)));

        validateEvents(TABLE_ADD_REQUESTED, TABLE_ADD_REQUESTED, TABLE_ADD_REQUESTED,
                TABLE_ADDED, TABLE_ADDED);
    }

    @Test
    public void extraneousFlow() {
        FlowTable f1 = flowTable((byte) 0, (byte) 0);
        FlowTable f2 = flowTable((byte) 1, (byte) 1);
        FlowTable f3 = flowTable((byte) 2, (byte) 2);
        mgr.applyFlowTables(f1, f2);

//        FlowTable updatedF1 = flowTable(f1, FlowTableState.ADDED);
//        FlowTable updatedF2 = flowTable(f2, FlowTableState.ADDED);
//        FlowTable updatedF3 = flowTable(f3, FlowTableState.ADDED);
        FlowTableEntry fe1 = new DefaultFlowTableEntry(f1);
        FlowTableEntry fe2 = new DefaultFlowTableEntry(f2);
        FlowTableEntry fe3 = new DefaultFlowTableEntry(f3);


        providerService.pushTableMetrics(DID, Lists.newArrayList(fe1, fe2, fe3));

        validateEvents(TABLE_ADD_REQUESTED, TABLE_ADD_REQUESTED, TABLE_ADDED, TABLE_ADDED);

    }

    /*
     * Tests whether a rule that was marked for removal but no flowRemoved was received
     * is indeed removed at the next stats update.
     */
    @Test
    public void flowMissingRemove() {
        FlowTable f1 = flowTable((byte) 0, (byte) 0);
        FlowTable f2 = flowTable((byte) 1, (byte) 1);
        FlowTable f3 = flowTable((byte) 2, (byte) 2);

//        FlowTable updatedF1 = flowTable(f1, FlowTableState.ADDED);
//        FlowTable updatedF2 = flowTable(f2, FlowTableState.ADDED);

        FlowTableEntry fe1 = new DefaultFlowTableEntry(f1);
        FlowTableEntry fe2 = new DefaultFlowTableEntry(f2);
        mgr.applyFlowTables(f1, f2, f3);

        mgr.removeFlowTables(f3);

        providerService.pushTableMetrics(DID, Lists.newArrayList(fe1, fe2));

        validateEvents(TABLE_ADD_REQUESTED, TABLE_ADD_REQUESTED, TABLE_ADD_REQUESTED,
                TABLE_REMOVE_REQUESTED, TABLE_ADDED, TABLE_ADDED, TABLE_REMOVED);

    }

    @Test
    public void getByAppId() {
        FlowTable f1 = flowTable((byte) 0, (byte) 0);
        FlowTable f2 = flowTable((byte) 1, (byte) 1);
        mgr.applyFlowTables(f1, f2);

        assertTrue("should have two rules",
                Lists.newLinkedList(mgr.getFlowTablesById(appId)).size() == 2);
    }

    @Test
    public void removeByAppId() {
        FlowTable f1 = flowTable((byte) 0, (byte) 0);
        FlowTable f2 = flowTable((byte) 1, (byte) 1);
        mgr.applyFlowTables(f1, f2);


        mgr.removeFlowTablesById(appId);

        //only check that we are in pending remove. Events and actual remove state will
        // be set by flowRemoved call.
        validateState(ImmutableMap.of(
                f1, FlowTableState.PENDING_REMOVE,
                f2, FlowTableState.PENDING_REMOVE));
    }

    @Test
    public void fallbackBasics() {
        FlowTable f1 = flowTable(FOO_DID, (byte) 0, (byte) 0);
        flowTables.clear();
        mgr.applyFlowTables(f1);
        assertTrue("flow rule not applied", flowTables.contains(f1));

        flowTables.clear();
        mgr.removeFlowTables(f1);
        assertTrue("flow rule not removed", flowTables.contains(f1));
    }

//    @Test
//    public void fallbackFlowRemoved() {
//        FlowTable f1 = flowTable(FOO_DID, (byte)0, (byte)0);
//        mgr.applyFlowTables(f1);
//        flowTables.clear();
//        providerService.flowtableRemoved(new DefaultFlowTableEntry(f1));
//        assertTrue("flow rule not reapplied", flowTables.contains(f1));
//    }
//
//    @Test
//    public void fallbackExtraFlow() {
//        FlowTable f1 = flowTable(FOO_DID, (byte)0, (byte)0);
//        flowTables.clear();
//        providerService.pushTableMetrics(FOO_DID, ImmutableList.of(new DefaultFlowTableEntry(f1)));
//        log.info("++++++++sqy flowTables.contains(f1):{}",flowTables.contains(f1));
//        assertTrue("flow rule not removed", flowTables.contains(f1));
//    }

//    @Test
//    public void fallbackPoll() {
//        FlowTableDriverProvider fallback = (FlowTableDriverProvider) mgr.defaultProvider();
//        FlowTable f1 = flowTable(FOO_DID, (byte)1, (byte)1);
//        mgr.applyFlowTables(f1);
//        FlowTableEntry fe = (FlowTableEntry) mgr.getFlowTables(FOO_DID).iterator().next();
//        assertEquals("incorrect state", FlowTableState.PENDING_ADD, fe.state());
//
//        fallback.init(fallback.providerService, mgr.deviceService, mgr.mastershipService, 1);
//        TestTools.assertAfter(2000, () -> {
//            FlowTableEntry e = (FlowTableEntry) mgr.getFlowTables(FOO_DID).iterator().next();
//            assertEquals("incorrect state", FlowTableState.ADDED, e.state());
//        });
//    }


    private static class TestListener implements FlowTableListener {
        final List<FlowTableEvent> events = new ArrayList<>();

        @Override
        public void event(FlowTableEvent event) {
            events.add(event);
        }
    }

    private static class TestDeviceService extends DeviceServiceAdapter {
        @Override
        public int getDeviceCount() {
            return 2;
        }

        @Override
        public Iterable<Device> getDevices() {
            return ImmutableList.of(DEV, FOO_DEV);
        }

        @Override
        public Iterable<Device> getAvailableDevices() {
            return getDevices();
        }

        @Override
        public Device getDevice(DeviceId deviceId) {
            return deviceId.equals(FOO_DID) ? FOO_DEV : DEV;
        }
    }

    private class TestProvider extends AbstractProvider implements FlowTableProvider {

        protected TestProvider(ProviderId id) {
            super(PID);
        }

        @Override
        public void applyFlowTable(FlowTable... flowTables) {
        }

        @Override
        public void applyTable(DeviceId deviceId, OFFlowTable ofTable) {

        }

        @Override
        public void removeFlowTable(FlowTable... flowTables) {
        }

        @Override
        public void removeTablesById(ApplicationId id, FlowTable... flowTables) {

        }

        @Override
        public void executeBatch(FlowTableBatchOperation batch) {
            // TODO: need to call batchOperationComplete
        }

        private class TestInstallationFuture
                implements ListenableFuture<CompletedTableBatchOperation> {

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public CompletedTableBatchOperation get()
                    throws InterruptedException, ExecutionException {
                return new CompletedTableBatchOperation(true, Collections.<FlowTable>emptySet(), null);
            }

            @Override
            public CompletedTableBatchOperation get(long timeout, TimeUnit unit)
                    throws InterruptedException,
                    ExecutionException, TimeoutException {
                return new CompletedTableBatchOperation(true, Collections.<FlowTable>emptySet(), null);
            }

            @Override
            public void addListener(Runnable task, Executor executor) {
                if (isDone()) {
                    executor.execute(task);
                }
            }
        }

    }

    public class TestApplicationId extends DefaultApplicationId {
        public TestApplicationId(int id, String name) {
            super(id, name);
        }
    }

    private class TestCoreService extends CoreServiceAdapter {

        @Override
        public IdGenerator getIdGenerator(String topic) {
            return new IdGenerator() {
                private AtomicLong counter = new AtomicLong(0);

                @Override
                public long getNewId() {
                    return counter.getAndIncrement();
                }
            };
        }
    }

    private class TestMastershipService extends MastershipServiceAdapter {
        @Override
        public MastershipRole getLocalRole(DeviceId deviceId) {
            return MastershipRole.MASTER;
        }
    }

    private class TestDriverManager extends DriverManager {
        TestDriverManager() {
            this.deviceService = mgr.deviceService;
            activate();
        }
    }

    static Collection<FlowTable> flowTables = new HashSet<>();

    public static class TestFlowTableProgrammable extends AbstractHandlerBehaviour implements FlowTableProgrammable {

        @Override
        public Collection<FlowTable> getFlowTables() {
            ImmutableList.Builder<FlowTable> builder = ImmutableList.builder();
            flowTables.stream().map(DefaultFlowTable::new).forEach(builder::add);
            return builder.build();
        }

        @Override
        public Collection<FlowTable> applyFlowTables(Collection<FlowTable> tables) {
            flowTables.addAll(tables);
            return tables;
        }

        @Override
        public Collection<FlowTable> removeFlowtables(Collection<FlowTable> tables) {
            flowTables.addAll(tables);
            return tables;
        }

    }


}
