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
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.flow.FlowRuleService;
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

    private final InternalDeviceProvider listener = new InternalDeviceProvider();

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
                sendPofFlowTables(deviceId);
            }
        }
        controller.addListener(listener);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {

        for (Device device:deviceService.getAvailableDevices()) {
            DeviceId deviceId = device.id();
            NodeId master = mastershipService.getMasterFor(deviceId);
            if (Objects.equals(local, master)) {
                removePofFlowTable(deviceId, 0);
            }
        }
        controller.removeListener(listener);
        log.info("Stopped");
    }

    public void changePorts(DeviceId deviceId) {
        if (deviceId.toString().split(":")[0].equals("pof")) {
            for (Port port:deviceService.getPorts(deviceId)) {
                if (!port.annotations().value(AnnotationKeys.PORT_NAME).equals("eth0")) {
                    deviceService.changePortState(deviceId, port.number(), true);
                }
            }
        }
    }

    public int sendPofFlowTables(DeviceId deviceId) {
        int tableId = (byte) tableStore.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);
        log.info("globalTableId: {}", tableId);

        byte smallTableId = tableStore.parseToSmallTableId(deviceId, tableId);
        log.info("smallTableId: {}", smallTableId);
        OFMatch20 ofMatch20= new OFMatch20();
        ofMatch20.setFieldId((short) 1);
        ofMatch20.setFieldName("test");
        ofMatch20.setOffset((short)0);
        ofMatch20.setLength((short) 48);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        match20List.add(ofMatch20);

        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId(smallTableId);
        ofFlowTable.setTableName("FirstEntryTable");
        ofFlowTable.setTableSize(128);
        ofFlowTable.setTableType(OFTableType.OF_MM_TABLE);
        ofFlowTable.setMatchFieldList(match20List);

        FlowTable.Builder flowTable = DefaultFlowTable.builder()
                .withFlowTable(ofFlowTable)
                .forTable(tableId)
                .forDevice(deviceId)
                .fromApp(appId);
        flowTableService.applyFlowTables(flowTable.build());

        return tableId;
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
            sendPofFlowTables(deviceId);
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
