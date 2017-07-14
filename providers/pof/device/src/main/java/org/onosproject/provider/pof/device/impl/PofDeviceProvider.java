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
package org.onosproject.provider.pof.device.impl;


import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.ChassisId;
import org.onosproject.floodlightpof.protocol.OFError;
import org.onosproject.floodlightpof.protocol.OFMessage;
import org.onosproject.floodlightpof.protocol.OFPhysicalPort;
import org.onosproject.floodlightpof.protocol.OFPortStatus;
import org.onosproject.floodlightpof.protocol.OFType;
import org.onosproject.floodlightpof.protocol.factory.BasicFactory;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DeviceId;
import org.onosproject.net.MastershipRole;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.SparseAnnotations;
import org.onosproject.net.device.DeviceProvider;
import org.onosproject.net.device.DeviceProviderRegistry;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.DeviceProviderService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.device.DefaultPortStatistics;
import org.onosproject.net.device.DeviceDescription;
import org.onosproject.net.device.DefaultDeviceDescription;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.device.DefaultPortDescription;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.table.FlowTableStore;
import org.onosproject.pof.controller.Dpid;
import org.onosproject.pof.controller.PofController;
import org.onosproject.pof.controller.PofEventListener;
import org.onosproject.pof.controller.PofSwitch;
import org.onosproject.pof.controller.PofSwitchListener;
import org.onosproject.pof.controller.RoleState;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.onosproject.net.DeviceId.deviceId;
import static org.onosproject.pof.controller.Dpid.dpid;
import static org.onosproject.pof.controller.Dpid.uri;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provider which uses an pof controller to detect network
 * infrastructure devices.
 */
@Component(immediate = true)
public class PofDeviceProvider extends AbstractProvider implements DeviceProvider {

    private static final Logger log = getLogger(PofDeviceProvider.class);

    //TODO consider renaming KBPS and MBPS (as they are used to convert by division)
    private static final long KBPS = 1_000;
    private static final long MBPS = 1_000 * 1_000;
//    private static final Frequency FREQ50 = Frequency.ofGHz(50);
//    private static final Frequency FREQ191_7 = Frequency.ofGHz(191_700);
//    private static final org.onlab.util.Frequency FREQ4_4 = org.onlab.util.Frequency.ofGHz(4_400);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceProviderRegistry providerRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PofController controller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected org.onosproject.cfg.ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore tableStore;

//    edited by hdy
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore store;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;
//    // edited by hdy
//    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
//    protected MastershipService mastershipService;
//
//    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
//    protected MastershipManager mastershipManager;
//
//    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
//    protected MastershipStore store;
//
//    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
//    protected ClusterService clusterService;


    private DeviceProviderService providerService;

    private final InternalDeviceProvider listener = new InternalDeviceProvider();

    // TODO: We need to make the poll interval configurable.
    static final int POLL_INTERVAL = 5;
    @Property(name = "PortStatsPollFrequency", intValue = POLL_INTERVAL,
            label = "Frequency (in seconds) for polling switch Port statistics")
    private int portStatsPollFrequency = POLL_INTERVAL;

    private HashMap<Dpid, PortStatsCollector> collectors = Maps.newHashMap();

    /**
     * Creates an pof device provider.
     */
    public PofDeviceProvider() {
        super(new ProviderId("pof", "org.onosproject.provider.pof"));
    }

    @Activate
    public void activate(ComponentContext context) {
        cfgService.registerProperties(getClass());
        providerService = providerRegistry.register(this);
        controller.addListener(listener);
        controller.addEventListener(listener);
        connectInitialDevices();
        log.info("Started");
    }

    @Deactivate
    public void deactivate(ComponentContext context) {
        cfgService.unregisterProperties(getClass(), false);
        listener.disable();
        controller.removeListener(listener);
        providerRegistry.unregister(this);
        collectors.values().forEach(PortStatsCollector::stop);
        providerService = null;
        log.info("Stopped");
    }

    @org.apache.felix.scr.annotations.Modified
    public void modified(ComponentContext context) {
        java.util.Dictionary<?, ?> properties = context.getProperties();
        int newPortStatsPollFrequency;
        try {
            String s = get(properties, "PortStatsPollFrequency");
            newPortStatsPollFrequency = isNullOrEmpty(s) ? portStatsPollFrequency : Integer.parseInt(s.trim());

        } catch (NumberFormatException | ClassCastException e) {
            newPortStatsPollFrequency = portStatsPollFrequency;
        }

        if (newPortStatsPollFrequency != portStatsPollFrequency) {
            portStatsPollFrequency = newPortStatsPollFrequency;
            collectors.values().forEach(psc -> psc.adjustPollInterval(portStatsPollFrequency));
        }

        log.info("Settings: portStatsPollFrequency={}", portStatsPollFrequency);
    }

    private void connectInitialDevices() {
        for (PofSwitch sw : controller.getSwitches()) {
            try {
                log.info("connectInitialDevices: {}", sw.getStringId());
                listener.switchAdded(new Dpid(sw.getId()));
            } catch (Exception e) {
                log.warn("Failed initially adding {} : {}", sw.getStringId(), e.getMessage());
                log.debug("Error details:", e);
                // disconnect to trigger switch-add later
                sw.disconnectSwitch();
            }
            PortStatsCollector psc = new PortStatsCollector(sw, portStatsPollFrequency);
            psc.start();
            collectors.put(new Dpid(sw.getId()), psc);
        }
    }

    @Override
    public boolean isReachable(DeviceId deviceId) {
        PofSwitch sw = controller.getSwitch(dpid(deviceId.uri()));
        return sw != null && sw.isConnected();
    }

    @Override
    public void triggerProbe(DeviceId deviceId) {
        log.debug("Triggering probe on device {}", deviceId);

        final Dpid dpid = dpid(deviceId.uri());
        PofSwitch sw = controller.getSwitch(dpid);
        if (sw == null || !sw.isConnected()) {
            log.error("Failed to probe device {} on sw={}", deviceId, sw);
            providerService.deviceDisconnected(deviceId);
            return;
        } else {
            log.trace("Confirmed device {} connection", deviceId);
        }

        // Prompt an update of port information. We can use any XID for this.
        BasicFactory fact = sw.factory();
        /*modified by hdy*/
        sw.sendMsg(fact.getOFMessage(OFType.FEATURES_REQUEST));
/*        switch (fact.getVersion()) {
            case OF_10:
                sw.sendMsg(fact.buildFeaturesRequest().setXid(0).build());
                break;
            case OF_13:
                sw.sendMsg(fact.buildPortDescStatsRequest().setXid(0).build());
                break;
            default:
                log.warn("Unhandled protocol version");
        }*/
    }

    @Override
    public void roleChanged(DeviceId deviceId, MastershipRole newRole) {
        switch (newRole) {
            case MASTER:
                controller.setRole(dpid(deviceId.uri()), RoleState.MASTER);
                break;
            case STANDBY:
                controller.setRole(dpid(deviceId.uri()), RoleState.EQUAL);
                break;
            case NONE:
                controller.setRole(dpid(deviceId.uri()), RoleState.SLAVE);
                break;
            default:
                log.error("Unknown Mastership state : {}", newRole);

        }
        log.debug("Accepting mastership role change to {} for device {}", newRole, deviceId);
    }

    @Override
    public void changePortState(DeviceId deviceId, PortNumber portNumber,
                                boolean enable) {
        final Dpid dpid = dpid(deviceId.uri());
        PofSwitch sw = controller.getSwitch(dpid);
        if (sw == null || !sw.isConnected()) {
            log.error("Failed to change portState on device {}", deviceId);
            return;
        }
        byte portEnable = 0;
        if (enable) {
            portEnable = 1;
        } else {
            portEnable = 0;
        }
        OFPhysicalPort port = sw.getPorts().get((int) portNumber.toLong());
        port.setOpenflowEnable(portEnable);

        log.info("port: " + port.toString());

        OFPortStatus msg = (OFPortStatus) sw.factory().getOFMessage(OFType.PORT_MOD);
        msg.setReason((byte) OFPortStatus.OFPortReason.OFPPR_MODIFY.ordinal());
        msg.setDesc(port);
        msg.setType(OFType.PORT_MOD);
        msg.setXid(1);
        msg.setLength((short) 136);
        log.info("msg: " + msg.toString());
        sw.sendMsg(msg);

    }
    private void pushPortMetrics(Dpid dpid, Collection<OFPhysicalPort> portStatsEntries) {
        DeviceId deviceId = deviceId(uri(dpid));
        Collection<PortStatistics> stats = buildPortStatistics(deviceId, portStatsEntries);
        providerService.updatePortStatistics(deviceId, stats);
    }

    private Collection<PortStatistics> buildPortStatistics(DeviceId deviceId, Collection<OFPhysicalPort> entries) {
        HashSet<PortStatistics> stats = Sets.newHashSet();
        for (OFPhysicalPort entry : entries) {
            try {
                if (entry == null || entry.getSlotPortId() == 0) {
                    return Collections.unmodifiableSet(stats);
                }
                DefaultPortStatistics.Builder builder = DefaultPortStatistics.builder();
                DefaultPortStatistics stat = builder.setDeviceId(deviceId)
                        .setPort(entry.getSlotPortId())
                        .setPacketsReceived(0)
                        .setPacketsSent(0)
                        .setBytesReceived(0)
                        .setBytesSent(0)
                        .setPacketsRxDropped(0)
                        .setPacketsTxDropped(0)
                        .setPacketsRxErrors(0)
                        .setPacketsTxErrors(0)
                        .setDurationSec(0)
                        .setDurationNano(0)
//                  .setDurationSec(entry.getVersion() == OF_10 ? 0 : entry.getDurationSec())
//                  .setDurationNano(entry.getVersion() == OF_10 ? 0 : entry.getDurationNsec())
                        .build();

                stats.add(stat);
            } catch (Exception e) {
                log.warn("Unable to process port stats", e);
            }
        }
        return java.util.Collections.unmodifiableSet(stats);
    }

    private class InternalDeviceProvider implements PofSwitchListener, PofEventListener {

        private  HashMap<Dpid, List<OFPhysicalPort>>  portStatsReplies = new HashMap<>();
        private boolean isDisabled = false;

        @Override
        public void switchAdded(Dpid dpid) {
            if (providerService == null) {
                return;
            }
            DeviceId did = deviceId(uri(dpid));
            PofSwitch sw = controller.getSwitch(dpid);
            if (sw == null) {
                return;
            }

            tableStore.initializeSwitchStore(did);

            ChassisId cId = new ChassisId(dpid.value());

            SparseAnnotations annotations = DefaultAnnotations.builder()
                    .set(AnnotationKeys.PROTOCOL, "POF")
                    .set(AnnotationKeys.CHANNEL_ID, sw.channelId())
                    .set(AnnotationKeys.MANAGEMENT_ADDRESS, sw.channelId().split(":")[0])
                    .build();

            DeviceDescription description =
                    new DefaultDeviceDescription(did.uri(), sw.deviceType(),
                            sw.manufacturerDescription(),
                            sw.hardwareDescription(),
                            sw.softwareDescription(),
                            sw.serialNumber(),
                            cId, annotations);
            //sw.handleRole();
            providerService.deviceConnected(did, description);
            providerService.updatePorts(did, buildPortDescriptions(sw));
            pushPortMetrics(dpid, sw.getPorts().values());

            PortStatsCollector psc =
                    new PortStatsCollector(sw, portStatsPollFrequency);
            psc.start();
            collectors.put(dpid, psc);

            //figure out race condition for collectors.remove() and collectors.put()
            if (controller.getSwitch(dpid) == null) {
                switchRemoved(dpid);
            }
        }

        @Override
        public void handleConnectionUp(Dpid dpid){

        }
        @Override
        public void switchRemoved(Dpid dpid) {
            if (providerService == null) {
                return;
            }
            tableStore.removeSwitchStore(deviceId(uri(dpid)));
            providerService.deviceDisconnected(deviceId(uri(dpid)));

            PortStatsCollector collector = collectors.remove(dpid);
            if (collector != null) {
                collector.stop();
            }
        }

        @Override
        public void switchChanged(Dpid dpid) {
            log.debug("switchChanged({})", dpid);
            if (providerService == null) {
                return;
            }
            DeviceId did = deviceId(uri(dpid));
            PofSwitch sw = controller.getSwitch(dpid);
            if (sw == null) {
                return;
            }
            final List<PortDescription> ports = buildPortDescriptions(sw);
            log.debug("switchChanged({}) {}", did, ports);
            providerService.updatePorts(did, ports);
        }

        @Override
        public void portChanged(Dpid dpid, OFPortStatus status) {
            log.debug("portChanged({},{})", dpid, status);
            PortDescription portDescription = buildPortDescription(status);
            providerService.portStatusChanged(deviceId(uri(dpid)), portDescription);
        }

        @Override
        public void setTableResource(Dpid dpid, OFFlowTableResource msg) {
            log.info("setFlowTableNoBase for table Store with {}", dpid);
            store.setFlowTableNoBase(deviceId(uri(dpid)), msg);
        }

        @Override
        public void receivedRoleReply(Dpid dpid, RoleState requested, RoleState response) {
            log.debug("receivedRoleReply({},{},{})", dpid, requested, response);
            MastershipRole request = roleOf(requested);
            MastershipRole reply = roleOf(response);
            providerService.receivedRoleReply(deviceId(uri(dpid)), request, reply);
        }

        /**
         * Translates a RoleState to the corresponding MastershipRole.
         *
         * @param response role state
         * @return a MastershipRole
         */
        private MastershipRole roleOf(RoleState response) {
            switch (response) {
                case MASTER:
                    return MastershipRole.MASTER;
                case EQUAL:
                    return MastershipRole.STANDBY;
                case SLAVE:
                    return MastershipRole.NONE;
                default:
                    log.warn("unknown role {}", response);
                    return null;
            }
        }

        /**
         * Builds a list of port descriptions for a given list of ports.
         *
         * @return list of portdescriptions
         */
        /*modified by hdy*/
        private List<PortDescription> buildPortDescriptions(PofSwitch sw) {
            final List<PortDescription> portDescs = new ArrayList<>(sw.getPorts().size());

/*            if (!((Device.Type.ROADM.equals(sw.deviceType())) ||
                    (Device.Type.OTN.equals(sw.deviceType())))) {
                  sw.getPorts().forEach(port -> portDescs.add(buildPortDescription(port.)));
            }
            deleted by hdy*/
            sw.getPorts().entrySet().forEach(port -> {
                portDescs.add(buildPortDescription(port.getValue()));
            });
            return portDescs;
        }

        /**
         * Creates an annotation for the port name if one is available.
         *
         * @param portName the port name
         * @param portMac the port mac
         * @return annotation containing the port name if one is found,
         *         null otherwise
         */
        private SparseAnnotations makePortAnnotation(String portName, String portMac) {
            SparseAnnotations annotations = null;
            String pName = Strings.emptyToNull(portName);
            String pMac = Strings.emptyToNull(portMac);
            if (portName != null) {
                annotations = DefaultAnnotations.builder()
                        .set(AnnotationKeys.PORT_NAME, pName)
                        .set(AnnotationKeys.PORT_MAC, pMac).build();
            }
            return annotations;
        }

        /**
         * Build a portDescription from a given Ethernet port description.
         *
         * @param port the port to build from.
         * @return portDescription for the port.
         */
        private PortDescription buildPortDescription(OFPhysicalPort port) {
            PortNumber portNo = PortNumber.portNumber(port.getSlotPortId());
            boolean enabled =
                    !(port.getState() == OFPhysicalPort.OFPortState.OFPPS_LINK_DOWN.getValue())
                            && !(port.getConfig() == OFPhysicalPort.OFPortConfig.OFPPC_PORT_DOWN.getValue());
            Port.Type type = Port.Type.PACKET;
            SparseAnnotations annotations = makePortAnnotation(port.getName(), port.getHardwareAddress().toString());
            return new DefaultPortDescription(portNo, enabled, type,
                    portSpeed(port), annotations);
        }


        private PortDescription buildPortDescription(OFPortStatus status) {
            OFPhysicalPort port = status.getDesc();
            if (status.getReason() != OFPortStatus.OFPortReason.OFPPR_DELETE.ordinal()) {
                return buildPortDescription(port);
            } else {
                PortNumber portNo = PortNumber.portNumber(port.getSlotPortId());
                Port.Type type = Port.Type.PACKET;
                SparseAnnotations annotations = makePortAnnotation(port.getName(), port
                        .getHardwareAddress().toString());
                return new DefaultPortDescription(portNo, false, type,
                        portSpeed(port), annotations);
            }
        }

        private long portSpeed(OFPhysicalPort port) {
            // Note: getCurrSpeed() returns a value in kbps (this also applies to OF_11 and OF_12)
            return port.getCurrentSpeed() / KBPS;
        }

        @Override
        public void handleMessage(Dpid dpid, OFMessage msg) {
            if (isDisabled) {
                return;
            }

            try {
                switch (msg.getType()) {
                    case PORT_STATUS:

                        break;
                    case ERROR:
                        if (((OFError) msg).getErrorType()
                                == OFError.OFErrorType.OFPET_PORT_MOD_FAILED.getValue()) {
                            log.error("port mod failed");
                        }
                    default:
                        break;
                }
            } catch (IllegalStateException e) {
                // system is shutting down and the providerService is no longer
                // valid. Messages cannot be processed.
            }
        }

        private void disable() {
            isDisabled = true;
        }
    }


}
