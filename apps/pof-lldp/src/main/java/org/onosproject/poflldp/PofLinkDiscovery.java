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
package org.onosproject.poflldp;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.OFPortStatus;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.mastership.MastershipService;
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
import org.onosproject.net.table.DefaultFlowTable;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableId;
import org.onosproject.net.table.FlowTableService;
import org.onosproject.net.table.FlowTableStore;
import org.onosproject.pof.controller.PofController;
import org.onosproject.pof.controller.Dpid;
import org.onosproject.pof.controller.PofSwitchListener;
import org.onosproject.pof.controller.RoleState;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceAdminService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An auxiliary application to send default pof flow tables for lldp
 */
@Component(immediate = true)
public class PofLinkDiscovery {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceAdminService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore tableStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableService flowTableService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PofController controller;

    private ApplicationId appId;

    private NodeId local;

    //private final InternalDeviceProvider listener = new InternalDeviceProvider();

    @Activate
    protected void activate() {

        appId = coreService.registerApplication("org.onosproject.poflldp");

        local = clusterService.getLocalNode().id();

        //Send flow tables to the switches that have been connected
        for (Device device : deviceService.getAvailableDevices()) {
            DeviceId deviceId = device.id();
            NodeId master = mastershipService.getMasterFor(deviceId);

            if (Objects.equals(local, master)) {
                changePorts(deviceId);
                int tableId1 = sendEtherTypeFlowTables(deviceId);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                sendMatchEtherTypeAndGotoTableFlowEntry(deviceId, tableId1, "0000", "0000");
                sendMatchEtherTypeAndPacketInFlowEntry(deviceId, tableId1, "88cc", "FFFF");
                sendMatchEtherTypeAndPacketInFlowEntry(deviceId, tableId1, "8942", "FFFF");
                //sendMatchEtherTypeAndDropFlowEntry(deviceId, tableId1, "0000", "0000");
                int tableId2 = sendEdgeFlowTables(deviceId);
                int tableId3 = sendVirtualFlowTables(deviceId);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                sendMatchAllPortAndGotoTableFlowEntry(deviceId, tableId2, tableId3);
            }
        }
        //controller.addListener(listener);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {

        for (Device device : deviceService.getAvailableDevices()) {
            DeviceId deviceId = device.id();
            NodeId master = mastershipService.getMasterFor(deviceId);
            if (Objects.equals(local, master)) {
                removePofFlowTable(deviceId, 0);
                removePofFlowTable(deviceId, 1);
                removePofFlowTable(deviceId, 2);
            }
        }
        //controller.removeListener(listener);
        log.info("Stopped");
    }

    public void changePorts(DeviceId deviceId) {
        if (deviceId.toString().split(":")[0].equals("pof")) {
            for (Port port : deviceService.getPorts(deviceId)) {
                if (!port.annotations().value(AnnotationKeys.PORT_NAME).equals("eth0")) {
                    deviceService.changePortState(deviceId, port.number(), true);
                }
            }
        }
    }

    public int sendEtherTypeFlowTables(DeviceId deviceId) {
        int tableId = (byte) tableStore.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);
        log.info("globalTableId: {}", tableId);

        byte smallTableId = tableStore.parseToSmallTableId(deviceId, tableId);
        log.info("smallTableId: {}", smallTableId);
        OFMatch20 ofMatch20= new OFMatch20();
        ofMatch20.setFieldId((short) 1);
        ofMatch20.setFieldName("EtherType");
        ofMatch20.setOffset((short)96);
        ofMatch20.setLength((short) 16);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        match20List.add(ofMatch20);

        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId(smallTableId);
        ofFlowTable.setTableName("EtherTypeTable");
        ofFlowTable.setTableSize(128);
        ofFlowTable.setTableType(OFTableType.OF_MM_TABLE);
        ofFlowTable.setMatchFieldList(match20List);
        ofFlowTable.setKeyLength((short) 16);
        ofFlowTable.setMatchFieldNum((byte)match20List.size());
        ofFlowTable.setCommand(null);

        FlowTable.Builder flowTable = DefaultFlowTable.builder()
                .withFlowTable(ofFlowTable)
                .forTable(tableId)
                .forDevice(deviceId)
                .fromApp(appId);
        flowTableService.applyFlowTables(flowTable.build());

        return tableId;
    }

    public int sendEdgeFlowTables(DeviceId deviceId) {
        int tableId = (byte) tableStore.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);
        log.info("globalTableId: {}", tableId);

        byte smallTableId = tableStore.parseToSmallTableId(deviceId, tableId);
        log.info("smallTableId: {}", smallTableId);

        //first is a special mac
        /*OFMatch20 om1 = new OFMatch20();
        om1.setFieldId((short) 1);
        om1.setOffset((short) 48);
        om1.setLength((short) 48);*/
        //then is the input port
        OFMatch20 om2 = new OFMatch20();
        om2.setFieldId((short)0xffff);
        om2.setOffset((short)16);
        om2.setLength((short)8);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        //match20List.add(om1);
        match20List.add(om2);

        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId(smallTableId);
        ofFlowTable.setTableName("EdgeTable");
        ofFlowTable.setTableSize(128);
        ofFlowTable.setTableType(OFTableType.OF_MM_TABLE);
        ofFlowTable.setMatchFieldList(match20List);
        ofFlowTable.setKeyLength((short)8);
        ofFlowTable.setMatchFieldNum((byte)match20List.size());
        ofFlowTable.setCommand(null);

        FlowTable.Builder flowTable = DefaultFlowTable.builder()
                .withFlowTable(ofFlowTable)
                .forTable(tableId)
                .forDevice(deviceId)
                .fromApp(appId);
        flowTableService.applyFlowTables(flowTable.build());

        return tableId;
    }

    public int sendVirtualFlowTables(DeviceId deviceId) {
        int tableId = (byte) tableStore.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);
        log.info("globalTableId: {}", tableId);

        byte smallTableId = tableStore.parseToSmallTableId(deviceId, tableId);
        log.info("smallTableId: {}", smallTableId);

        //first is a special mac
        /*OFMatch20 om1 = new OFMatch20();
        om1.setFieldId((short) 1);
        om1.setOffset((short) 0);
        om1.setLength((short) 48);*/
        //then is the input port
        OFMatch20 om2 = new OFMatch20();
        om2.setFieldId((short)0xffff);
        om2.setOffset((short)16);
        om2.setLength((short)8);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        //match20List.add(om1);
        match20List.add(om2);

        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId(smallTableId);
        ofFlowTable.setTableName("VirtualTable");
        ofFlowTable.setTableSize(128);
        ofFlowTable.setTableType(OFTableType.OF_MM_TABLE);
        ofFlowTable.setMatchFieldList(match20List);
        ofFlowTable.setKeyLength((short)8);
        ofFlowTable.setMatchFieldNum((byte)match20List.size());
        ofFlowTable.setCommand(null);

        FlowTable.Builder flowTable = DefaultFlowTable.builder()
                .withFlowTable(ofFlowTable)
                .forTable(tableId)
                .forDevice(deviceId)
                .fromApp(appId);
        flowTableService.applyFlowTables(flowTable.build());

        return tableId;
    }

    int sendMatchEtherTypeAndPacketInFlowEntry(DeviceId deviceId, int tableId, String value, String mask) {

        int newFlowEntryId = tableStore.getNewFlowEntryId(deviceId, tableId);

        //construct selector
        TrafficSelector.Builder pbuilder = DefaultTrafficSelector.builder();
        ArrayList<Criterion> entryList = new ArrayList<Criterion>();
        entryList.add(Criteria.matchOffsetLength((short)1, (short)96, (short)16, value, mask));
        pbuilder.add(Criteria.matchOffsetLength(entryList));

        //construct treatment
        TrafficTreatment.Builder ppbuilder = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(DefaultPofActions.packetIn(1).action());
        ppbuilder.add(DefaultPofInstructions.applyActions(actions));

        TrafficSelector selector = pbuilder.build();
        TrafficTreatment treatment = ppbuilder.build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .forTable(tableId)
                .forDevice(deviceId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(4000)
                .makePermanent()
                .withCookie(newFlowEntryId)
                .build();

        //flowRuleService.applyRule(flowRule1);
        flowRuleService.applyFlowRules(flowRule);
        return newFlowEntryId;
    }

    int sendMatchEtherTypeAndDropFlowEntry(DeviceId deviceId, int tableId, String value, String mask) {

        int newFlowEntryId = tableStore.getNewFlowEntryId(deviceId, tableId);

        //construct selector
        TrafficSelector.Builder pbuilder = DefaultTrafficSelector.builder();
        ArrayList<Criterion> entryList = new ArrayList<Criterion>();
        entryList.add(Criteria.matchOffsetLength((short)1, (short)96, (short)16, value, mask));
        pbuilder.add(Criteria.matchOffsetLength(entryList));

        //construct treatment
        TrafficTreatment.Builder ppbuilder = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(DefaultPofActions.drop(0).action());
        ppbuilder.add(DefaultPofInstructions.applyActions(actions));

        TrafficSelector selector = pbuilder.build();
        TrafficTreatment treatment = ppbuilder.build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .forTable(tableId)
                .forDevice(deviceId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(0)
                .makePermanent()
                .withCookie(newFlowEntryId)
                .build();

        //flowRuleService.applyRule(flowRule1);
        flowRuleService.applyFlowRules(flowRule);
        return newFlowEntryId;
    }

    int sendMatchEtherTypeAndGotoTableFlowEntry(DeviceId deviceId, int tableId, String value, String mask) {

        int newFlowEntryId = tableStore.getNewFlowEntryId(deviceId, tableId);

        //first is a special mac
        OFMatch20 om1 = new OFMatch20();
        om1.setFieldId((short) 1);
        om1.setOffset((short) 48);
        om1.setLength((short) 48);
        //then is the input port
        OFMatch20 om2 = new OFMatch20();
        om2.setFieldId((short)0xffff);
        om2.setOffset((short)16);
        om2.setLength((short)8);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        //match20List.add(om1);
        //match20List.add(om2);

        //construct selector
        TrafficSelector.Builder pbuilder = DefaultTrafficSelector.builder();
        ArrayList<Criterion> entryList = new ArrayList<Criterion>();
        entryList.add(Criteria.matchOffsetLength((short)1, (short)96, (short)16, value, mask));
        pbuilder.add(Criteria.matchOffsetLength(entryList));

        //construct treatment
        TrafficTreatment.Builder ppbuilder = DefaultTrafficTreatment.builder();
        //instructions: gotoTable
        ppbuilder.add(DefaultPofInstructions.gotoTable((byte)1, (byte)0, (short)0, match20List));

        TrafficSelector selector = pbuilder.build();
        TrafficTreatment treatment = ppbuilder.build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .forTable(tableId)
                .forDevice(deviceId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(0)
                .makePermanent()
                .withCookie(newFlowEntryId)
                .build();

        //flowRuleService.applyRule(flowRule1);
        flowRuleService.applyFlowRules(flowRule);
        return newFlowEntryId;
    }

    int sendMatchAllPortAndGotoTableFlowEntry(DeviceId deviceId, int tableId, int gotoTableId) {
        int newFlowEntryId = tableStore.getNewFlowEntryId(deviceId, tableId);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();

        //construct selector
        TrafficSelector.Builder pbuilder = DefaultTrafficSelector.builder();
        ArrayList<Criterion> entryList = new ArrayList<Criterion>();
        //entryList.add(Criteria.matchOffsetLength((short)1, (short)48, (short)48, "000000000000", "000000000000"));
        entryList.add(Criteria.matchOffsetLength((short)0xffff, (short)16, (short)8, "00", "00"));
        pbuilder.add(Criteria.matchOffsetLength(entryList));

        //construct treatment
        TrafficTreatment.Builder ppbuilder = DefaultTrafficTreatment.builder();
        //instructions: gotoTable
        ppbuilder.add(DefaultPofInstructions.gotoTable((byte)gotoTableId, (byte)0, (short)0, match20List));

        TrafficSelector selector = pbuilder.build();
        TrafficTreatment treatment = ppbuilder.build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .forTable(tableId)
                .forDevice(deviceId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(0)
                .makePermanent()
                .withCookie(newFlowEntryId)
                .build();

        //flowRuleService.applyRule(flowRule1);
        flowRuleService.applyFlowRules(flowRule);
        return newFlowEntryId;
    }

    public void removePofFlowTable(DeviceId deviceId, int tableId) {
        //flowTableService.removeFlowEntryByEntryId(deviceId, globalTableId1, newFlowEntryId1);
        log.info("++++ before removeFlowTablesByTableId: {}", tableId);
        flowTableService.removeFlowTablesByTableId(deviceId, FlowTableId.valueOf(tableId));
    }

    private class InternalDeviceProvider implements PofSwitchListener {
        @Override
        public void switchAdded(Dpid dpid) {
        }
        @Override
        public void handleConnectionUp(Dpid dpid){
            DeviceId deviceId = DeviceId.deviceId(Dpid.uri(dpid));
            changePorts(deviceId);
            int tableId1 = sendEtherTypeFlowTables(deviceId);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendMatchEtherTypeAndGotoTableFlowEntry(deviceId, tableId1, "0000", "0000");
            sendMatchEtherTypeAndPacketInFlowEntry(deviceId, tableId1, "88cc", "FFFF");
            sendMatchEtherTypeAndPacketInFlowEntry(deviceId, tableId1, "8942", "FFFF");
            //sendMatchEtherTypeAndDropFlowEntry(deviceId, tableId1, "0000", "0000");
            int tableId2 = sendEdgeFlowTables(deviceId);
            int tableId3 = sendVirtualFlowTables(deviceId);
        }
        @Override
        public void switchRemoved(Dpid dpid) {
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
}
