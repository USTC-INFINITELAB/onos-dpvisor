package org.onosproject.incubator.net.virtual.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;

import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.incubator.net.virtual.VirtualLink;
import org.onosproject.incubator.net.virtual.VirtualNetworkFlowService;
import org.onosproject.incubator.net.virtual.VirtualNetworkService;
import org.onosproject.incubator.net.virtual.VirtualPort;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;

import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.table.DefaultFlowTable;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableId;
import org.onosproject.net.table.FlowTableService;
import org.onosproject.net.table.FlowTableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of virtual network flow service
 */
@Component(immediate = true, enabled = false)
@Service
public class VirtualNetworkFlowManager implements VirtualNetworkFlowService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VirtualNetworkService vnService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore tableStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableService flowTableService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    private final DeviceListener deviceListener = new InnerDeviceListener();

    private ApplicationId appId;

    @Activate
    public void activate() {
        appId = coreService.registerApplication("org.onosproject.virtual-network-flows");

        for (Device device : deviceService.getAvailableDevices()) {
            DeviceId deviceId = device.id();
        }

        deviceService.addListener(deviceListener);

        log.info("Started");
    }

    @Deactivate
    public void deactivate() {

        deviceService.removeListener(deviceListener);

        log.info("Stopped");
    }

    @Override
    public int getPhysicalTableId(NetworkId networkId, DeviceId deviceId,
                                  OFTableType tableType, int tableId) {
        //TODO:
        return 2;
    }

    public void sendEdgeEntries(NetworkId networkId, DeviceId deviceId, PortNumber ingressPort) {
        ConnectPoint ingressCp = new ConnectPoint(deviceId, ingressPort);
        //find the virtual link ended with this connection point
        Optional<VirtualLink> optionalIngressLink = vnService
                .getVirtualLinks(networkId)
                .stream()
                .filter(l -> l.dst().equals(ingressCp))
                .findFirst();
        if(optionalIngressLink.isPresent()) {

        }
    }

    public int sendEdgeFlowTable(DeviceId deviceId) {
        int tableId = tableStore.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);
        log.info("globalTableId: {}", tableId);
        byte smallTableId = tableStore.parseToSmallTableId(deviceId, tableId);
        log.info("test for smallTableId: {}", smallTableId);
        OFMatch20 ofMatch20= new OFMatch20();
        ofMatch20.setFieldId((short) 1);
        ofMatch20.setFieldName("test");
        ofMatch20.setOffset((short)0);
        ofMatch20.setLength((short) 48);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        match20List.add(ofMatch20);

        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId((byte) tableId);
        ofFlowTable.setTableName("EdgeTable");
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

    public void removePofFlowTable(DeviceId deviceId, byte tableId) {

        //flowTableService.removeFlowEntryByEntryId(deviceId, globalTableId1, newFlowEntryId1);
        log.info("++++ before removeFlowTablesByTableId: {}", tableId);
        flowTableService.removeFlowTablesByTableId(deviceId, FlowTableId.valueOf(tableId));
    }

    /**
     * Inner Device Event Listener class.
     */
    private class InnerDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {

            DeviceId deviceId = event.subject().id();
            if (event.type() == DeviceEvent.Type.DEVICE_ADDED || event.type() == DeviceEvent.Type.DEVICE_UPDATED) {
                log.info("Device Event: time = {} type = {} event = {}",
                         event.time(), event.type(), event);
                //device added, send default flow tables
            }
        }
    }

}
