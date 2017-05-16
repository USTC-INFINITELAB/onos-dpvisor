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

package org.onosproject.incubator.net.virtual.impl;

import com.google.common.collect.Lists;
import org.onosproject.core.ApplicationId;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.floodlightpof.util.HexString;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.incubator.net.virtual.VirtualLink;
import org.onosproject.incubator.net.virtual.VirtualNetworkFlowService;
import org.onosproject.incubator.net.virtual.VirtualNetworkService;
import org.onosproject.incubator.net.virtual.VirtualPort;
import org.onosproject.incubator.net.virtual.event.AbstractVirtualListenerManager;
import org.onosproject.incubator.net.virtual.provider.VirtualProviderRegistryService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.DefaultPofActions;
import org.onosproject.net.flow.instructions.DefaultPofInstructions;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.table.CompletedTableBatchOperation;
import org.onosproject.net.table.DefaultFlowTable;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableEntry;
import org.onosproject.net.table.FlowTableEvent;
import org.onosproject.net.table.FlowTableId;
import org.onosproject.net.table.FlowTableListener;
import org.onosproject.net.table.FlowTableOperation;
import org.onosproject.net.table.FlowTableOperations;
import org.onosproject.net.table.FlowTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Flow table service implementation built on the virtual network service.
 */
public class VirtualNetworkPofTableManager
        extends AbstractVirtualListenerManager<FlowTableEvent, FlowTableListener>
        implements FlowTableService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final int EDGE_TABLE_ID = 1;
    private static final int VIRTUAL_TABLE_ID = 2;

    private final DeviceService deviceService;

    private VirtualProviderRegistryService providerRegistryService = null;

    private VirtualNetworkFlowService virtualNetworkFlowService = null;

    private FlowTableService flowTableService = null;
    private FlowRuleService flowRuleService = null;

    public VirtualNetworkPofTableManager(VirtualNetworkService virtualNetworkManager,
                                         NetworkId networkId) {
        super(virtualNetworkManager, networkId, FlowTableEvent.class);

        providerRegistryService =
                serviceDirectory.get(VirtualProviderRegistryService.class);
        //innerProviderService = new InternalPofTableProviderService();
        //providerRegistryService.registerProviderService(networkId(), innerProviderService);

        deviceService = manager.get(networkId, DeviceService.class);

        //virtualNetworkFlowService = serviceDirectory.get(VirtualNetworkFlowService.class);

        flowTableService = serviceDirectory.get(FlowTableService.class);
        flowRuleService = serviceDirectory.get(FlowRuleService.class);
    }

    @Override
    public int getNewGlobalFlowTableId(DeviceId deviceId, OFTableType type) {
        //At present, we only support one-to-one mapping
        Set<DeviceId> physicalDeviceIdSet = manager.getPhysicalDevices(networkId, deviceId);
        DeviceId physicalDeviceId = physicalDeviceIdSet.iterator().next();
        int tableId = flowTableService.getNewGlobalFlowTableId(physicalDeviceId, type);

        //send flow entries for the EdgeTable and VirtualTable to support virtualization
        //Port-based network virtualization

        Set<VirtualPort> virtualPortSet = manager.getVirtualPorts(networkId, deviceId);
        Set<VirtualLink> virtualLinkSet = manager.getVirtualLinks(networkId);
        for (VirtualPort virtualPort : virtualPortSet) {
            ConnectPoint cp =new ConnectPoint(virtualPort.element().id(), virtualPort.number());
            Optional<VirtualLink> optionalIngressLink = virtualLinkSet
                    .stream()
                    .filter(l -> l.dst().equals(cp))
                    .findFirst();
            if (!optionalIngressLink.isPresent()) {
                //edge port, send flow entries to EdgeTable
                ConnectPoint realizedCp = virtualPort.realizedBy();
                sendFlowEntryToEdgeTable(realizedCp.deviceId(), EDGE_TABLE_ID, (int) realizedCp.port().toLong(), tableId);
            } else {
                //not edge port, send flow entries to VirtualTable
                ConnectPoint realizedCp = virtualPort.realizedBy();
                sendFlowEntryToVirtualTable(realizedCp.deviceId(), VIRTUAL_TABLE_ID, (int) realizedCp.port().toLong(), tableId);
            }
        }

        return tableId;
    }

    private void sendFlowEntryToVirtualTable(DeviceId deviceId, int virtualTableId, int inPort, int gotoTableId) {
        //match ingress port
        //delete the external packet header field for virtualization
        //apply the goto table action
        int newFlowEntryId = flowTableService.getNewFlowEntryId(deviceId, virtualTableId);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();

        //construct selector
        TrafficSelector.Builder pbuilder = DefaultTrafficSelector.builder();
        ArrayList<Criterion> entryList = new ArrayList<Criterion>();
        //entryList.add(Criteria.matchOffsetLength((short)1, (short)48, (short)48, "000000000000", "000000000000"));
        entryList.add(Criteria.matchOffsetLength((short)0xffff, (short)16, (short)8, HexString.toHex((byte)inPort), "ff"));
        pbuilder.add(Criteria.matchOffsetLength(entryList));

        //construct treatment
        TrafficTreatment.Builder ppbuilder = DefaultTrafficTreatment.builder();
        //apply instruction: delete field
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(DefaultPofActions.deleteField(0, 112).action());
        ppbuilder.add(DefaultPofInstructions.applyActions(actions));
        //instruction: gotoTable
        ppbuilder.add(DefaultPofInstructions.gotoTable((byte)gotoTableId, (byte)0, (short)0, match20List));

        TrafficSelector selector = pbuilder.build();
        TrafficTreatment treatment = ppbuilder.build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .forTable(virtualTableId)
                .forDevice(deviceId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(1)
                .makePermanent()
                .withCookie(newFlowEntryId)
                .build();

        //flowRuleService.applyRule(flowRule1);
        flowRuleService.applyFlowRules(flowRule);
    }

    private void sendFlowEntryToEdgeTable(DeviceId deviceId, int edgeTableId, int inPort, int gotoTableId) {
        //match ingress port
        //apply the goto table action
        int newFlowEntryId = flowTableService.getNewFlowEntryId(deviceId, edgeTableId);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();

        //construct selector
        TrafficSelector.Builder pbuilder = DefaultTrafficSelector.builder();
        ArrayList<Criterion> entryList = new ArrayList<Criterion>();
        //entryList.add(Criteria.matchOffsetLength((short)1, (short)48, (short)48, "000000000000", "000000000000"));
        entryList.add(Criteria.matchOffsetLength((short)0xffff, (short)16, (short)8, HexString.toHex((byte)inPort), "ff"));
        pbuilder.add(Criteria.matchOffsetLength(entryList));

        //construct treatment
        TrafficTreatment.Builder ppbuilder = DefaultTrafficTreatment.builder();
        //instructions: gotoTable
        ppbuilder.add(DefaultPofInstructions.gotoTable((byte)gotoTableId, (byte)0, (short)0, match20List));

        TrafficSelector selector = pbuilder.build();
        TrafficTreatment treatment = ppbuilder.build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .forTable(edgeTableId)
                .forDevice(deviceId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(1)
                .makePermanent()
                .withCookie(newFlowEntryId)
                .build();

        //flowRuleService.applyRule(flowRule1);
        flowRuleService.applyFlowRules(flowRule);

    }

    @Override
    public int getNewFlowEntryId(DeviceId deviceId, int tableId) {
        //At present, we only support one-to-one mapping
        Set<DeviceId> physicalDeviceIdSet = manager.getPhysicalDevices(networkId, deviceId);
        DeviceId physicalDeviceId = physicalDeviceIdSet.iterator().next();
        return flowTableService.getNewFlowEntryId(physicalDeviceId, tableId);
    }

    @Override
    public int getFlowTableCount() {
        return 0;
    }

    @Override
    public Iterable<FlowTable> getFlowTables(DeviceId deviceId) {
        return null;
    }

    @Override
    public void applyFlowTables(FlowTable... flowTables) {
        for (FlowTable flowTable : flowTables) {
            //devirtualize flow table
            int tableId = (int)flowTable.id().value();
            OFFlowTable ofFlowTable = flowTable.flowTable();
            DeviceId deviceId = flowTable.deviceId();
            Set<DeviceId> physicalDeviceIdSet = manager.getPhysicalDevices(networkId, deviceId);
            DeviceId physicalDeviceId = physicalDeviceIdSet.iterator().next();

            ApplicationId appId = manager.getVirtualNetworkApplicationId(networkId);
            //build physical flow tables and  send them to physical switches
            FlowTable newFlowTable = DefaultFlowTable.builder()
                    .withFlowTable(ofFlowTable)
                    .forTable(tableId)
                    .forDevice(physicalDeviceId)
                    .fromApp(appId)
                    .build();
            flowTableService.applyFlowTables(newFlowTable);
        }

    }

    @Override
    public void removeFlowTables(FlowTable... flowTables) {

    }

    @Override
    public void removeFlowTablesById(ApplicationId appId) {

    }

    @Override
    public void removeFlowTablesByTableId(DeviceId deviceId, FlowTableId tableId) {

    }

    @Override
    public void removeFlowEntryByEntryId(DeviceId deviceId, int globalTableId, long entryId) {

    }

    @Override
    public Iterable<FlowTable> getFlowTablesById(ApplicationId id) {
        return null;
    }

    @Override
    public FlowTable getFlowTablesByTableId(DeviceId deviceId, FlowTableId tableId) {
        return null;
    }

    @Override
    public void apply(FlowTableOperations ops) {

    }

    @Override
    public void applyTable(DeviceId deviceId, OFFlowTable ofTable, ApplicationId appId) {

    }

/*
    private final class InternalPofTableProviderService
            extends AbstractVirtualProviderService<VirtualPofTableProvider>
            implements VirtualPofTableProviderService {

        private InternalPofTableProviderService() {
            //TODO: find a proper virtual provider.
            Set<ProviderId> providerIds =
                    providerRegistryService.getProvidersByService(this);
            ProviderId providerId = providerIds.stream().findFirst().get();
            VirtualPofTableProvider provider = (VirtualPofTableProvider)
                    providerRegistryService.getProvider(providerId);
            setProvider(provider);
        }

        @Override
        public void flowTableRemoved(FlowTable flowTable) {

        }

        @Override
        public void pushTableMetrics(DeviceId deviceId, Iterable<FlowTableEntry> flowTables) {

        }

        @Override
        public void pushTableMetricsWithoutFlowMissing(DeviceId deviceId, Iterable<FlowTableEntry> flowTables) {

        }

        @Override
        public void batchOperationCompleted(long batchId, CompletedTableBatchOperation operation) {

        }
    }
*/

    }