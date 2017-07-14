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

package org.onosproject.pof.controller.driver;

import com.google.common.collect.Lists;
import org.jboss.netty.channel.Channel;
import org.onlab.packet.IpAddress;
import org.onosproject.floodlightpof.protocol.OFError;
import org.onosproject.floodlightpof.protocol.OFFeaturesReply;
import org.onosproject.floodlightpof.protocol.OFMessage;
import org.onosproject.floodlightpof.protocol.OFPhysicalPort;
import org.onosproject.floodlightpof.protocol.OFPortStatus;
import org.onosproject.floodlightpof.protocol.OFRoleReply;
import org.onosproject.floodlightpof.protocol.OFRoleRequest;
import org.onosproject.floodlightpof.protocol.OFType;
import org.onosproject.floodlightpof.protocol.factory.BasicFactory;
import org.onosproject.floodlightpof.protocol.statistics.OFDescriptionStatistics;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.floodlightpof.protocol.table.OFTableResource;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.Device;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.pof.controller.Dpid;
import org.onosproject.pof.controller.PofEventListener;
import org.onosproject.pof.controller.RoleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.onlab.util.Tools.groupedThreads;

/**
 * An abstract representation of an POF switch. Can be extended by others
 * to serve as a base for their vendor specific representation of a switch.
 */
public abstract class AbstractPofSwitch extends AbstractHandlerBehaviour
        implements PofSwitchDriver {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private Channel channel;
    protected String channelId;

    private boolean connected;
    protected boolean startDriverHandshakeCalled = false;
    private Dpid dpid;
    private PofAgent agent;
    private final AtomicInteger xidCounter = new AtomicInteger(0);

    protected Map<Integer, OFPhysicalPort> ports = new ConcurrentHashMap<Integer, OFPhysicalPort>();
    //wenjian

    protected boolean tableFull;


    protected Map<OFTableType, OFTableResource> ofTableTypeOFTableResourceMap
            = new ConcurrentHashMap<OFTableType, OFTableResource>();

    private RoleHandler roleMan;

    // TODO this is accessed from multiple threads, but volatile may have performance implications
    protected volatile RoleState role;

    protected OFFeaturesReply features;

    protected OFDescriptionStatistics desc;

    protected Set<PofEventListener> pofOutgoingMsgListener = new CopyOnWriteArraySet<>();

    protected ExecutorService executorMsgs =
            Executors.newCachedThreadPool(groupedThreads("onos/pof", "event-outgoing-msg-stats-%d", log));

    // messagesPendingMastership is used as synchronization variable for
    // all mastership related changes. In this block, mastership (including
    // role update) will have either occurred or not.
    private final AtomicReference<List<OFMessage>> messagesPendingMastership
            = new AtomicReference<>();

    /*wenjian
        modified the init, origin is init(Dpid dpid, OFDescStatsReply desc, OFVersion ofv)
     */
    @Override
    public void init(Dpid dpId, OFDescriptionStatistics deSc) {
        this.dpid = dpId;
        this.desc = deSc;
        //this.ofVersion = ofv;
    }

    //************************
    // Channel related
    //************************

    @Override
    public final void disconnectSwitch() {
        setConnected(false);
        this.channel.close();
    }

    @Override
    public void sendMsg(OFMessage msg) {
        this.sendMsg(Collections.singletonList(msg));
    }

    @Override
    public final void sendMsg(List<OFMessage> msgs) {
        /*
           It is possible that in this block, we transition to SLAVE/EQUAL.
           If this is the case, the supplied messages will race with the
           RoleRequest message, and they could be rejected by the switch.
           In the interest of performance, we will not protect this block with
           a synchronization primitive, because the message would have just been
           dropped anyway.
        */
        if (role == RoleState.MASTER) {
            // fast path send when we are master

            sendMsgsOnChannel(msgs);
            return;
        }
        // check to see if mastership transition is in progress
        synchronized (messagesPendingMastership) {
            /*
               messagesPendingMastership is used as synchronization variable for
               all mastership related changes. In this block, mastership (including
               role update) will have either occurred or not.
            */
            if (role == RoleState.MASTER) {
                // transition to MASTER complete, send messages
                sendMsgsOnChannel(msgs);
                return;
            }

            List<OFMessage> messages = messagesPendingMastership.get();
            if (messages != null) {
                // we are transitioning to MASTER, so add messages to queue
                messages.addAll(msgs);
                log.debug("Enqueue message for switch {}. queue size after is {}",
                          dpid, messages.size());
            } else {
                // not transitioning to MASTER
                log.warn("Dropping message for switch {} (role: {}, connected: {}): {}",
                         dpid, role, channel.isConnected(), msgs);
            }
        }
    }

    private void countOutgoingMsg(List<OFMessage> msgs) {
        // listen to outgoing control messages only if listeners are registered
        if (pofOutgoingMsgListener.size() != 0) {
            msgs.forEach(m -> {
                if (m.getType() == OFType.PACKET_OUT ||
                        m.getType() == OFType.FLOW_MOD ||
                        //wenjian, stats_request is multipart_request in pof
                        //m.getType() == OFType.STATS_REQUEST) {
                        m.getType() == OFType.MULTIPART_REQUEST) {
                    executorMsgs.execute(new OFMessageHandler(dpid, m));
                }
            });
        }
    }

    private void sendMsgsOnChannel(List<OFMessage> msgs) {
        if (channel.isConnected()) {
            channel.write(msgs);
            countOutgoingMsg(msgs);
        } else {
            log.warn("Dropping messages for switch {} because channel is not connected: {}",
                     dpid, msgs);
        }
    }

    @Override
    public final void sendRoleRequest(OFMessage msg) {
        if (msg instanceof OFRoleRequest) {
            sendMsgsOnChannel(Collections.singletonList(msg));
            return;
        }
        throw new IllegalArgumentException("Someone is trying to send " +
                                                   "a non role request message");
    }

    @Override
    public final void
    sendHandshakeMessage(OFMessage message) {
        if (!this.isDriverHandshakeComplete()) {
            sendMsgsOnChannel(Collections.singletonList(message));
        }
    }

    @Override
    public final boolean isConnected() {
        return this.connected;
    }

    @Override
    public final void setConnected(boolean connected) {
        this.connected = connected;
    }

    @Override
    public final void setChannel(Channel channel) {
        this.channel = channel;
        final SocketAddress address = channel.getRemoteAddress();
        if (address instanceof InetSocketAddress) {
            final InetSocketAddress inetAddress = (InetSocketAddress) address;
            final IpAddress ipAddress = IpAddress.valueOf(inetAddress.getAddress());
            if (ipAddress.isIp4()) {
                channelId = ipAddress.toString() + ':' + inetAddress.getPort();
            } else {
                channelId = '[' + ipAddress.toString() + "]:" + inetAddress.getPort();
            }
        }
    }

    @Override
    public String channelId() {
        return channelId;
    }

    //************************
    // Switch features related
    //************************

    @Override
    public final long getId() {
        return this.dpid.value();
    }

    @Override
    public final String getStringId() {
        return this.dpid.toString();
    }

    /*wenjian
    @Override
    public final void setOFVersion(OFVersion ofV) {
        this.ofVersion = ofV;
    }
    */

    @Override
    public void setTableFull(boolean full) {
        this.tableFull = full;
    }

    @Override
    public void setFeaturesReply(OFFeaturesReply featuresReply) {
        this.features = featuresReply;
    }

    @Override
    public abstract Boolean supportNxRole();

    //************************
    //  Message handling
    //************************
    /**
     * Handle the message coming from the dataplane.
     *
     * @param m the actual message
     */
    @Override
    public final void handleMessage(OFMessage m) {
        //modify by hhb
        if (this.role == RoleState.MASTER
                || m instanceof OFPortStatus
                || m instanceof OFFlowTableResource) {
            this.agent.processMessage(dpid, m);
        } else {
            log.trace("Dropping received message {}, was not MASTER", m);
        }
    }

    @Override
    public RoleState getRole() {
        return role;
    }

    @Override
    public final boolean connectSwitch() {
        return this.agent.addConnectedSwitch(dpid, this);
    }

    @Override
    public final void handleConnectionUp() {
        this.agent.handleConnectionUp(dpid, this);
    }

    @Override
    public final boolean activateMasterSwitch() {
        return this.agent.addActivatedMasterSwitch(dpid, this);
    }

    @Override
    public final boolean activateEqualSwitch() {
        return this.agent.addActivatedEqualSwitch(dpid, this);
    }

    @Override
    public final void transitionToEqualSwitch() {
        this.agent.transitionToEqualSwitch(dpid);
    }

    @Override
    public final void transitionToMasterSwitch() {
        this.agent.transitionToMasterSwitch(dpid);
        synchronized (messagesPendingMastership) {
            List<OFMessage> messages = messagesPendingMastership.get();
            if (messages != null) {
                // Cannot use sendMsg here. It will only append to pending list.
                sendMsgsOnChannel(messages);
                log.info("Sending {} pending messages to switch {}",
                         messages.size(), dpid);
                messagesPendingMastership.set(null);
            }
            // perform role transition after clearing messages queue
            log.info("+++++ the switch is set as master~");
            this.role = RoleState.MASTER;
        }
    }

    @Override
    public final void removeConnectedSwitch() {
        this.agent.removeConnectedSwitch(dpid);
    }

    @Override
    public void addEventListener(PofEventListener listener) {
        pofOutgoingMsgListener.add(listener);
    }

    @Override
    public void removeEventListener(PofEventListener listener) {
        pofOutgoingMsgListener.remove(listener);
    }

    /*wenjian
    @Override
    public OFFactory factory() {
        return OFFactories.getFactory(ofVersion);
    }
    */
    //wenjian
    public BasicFactory factory() {
        return new BasicFactory();
    }

    /*wenjian
    @Override
    public void setPortDescReply(OFPortDescStatsReply portDescReply) {
        this.ports.add(portDescReply);
    }

    @Override
    public void setPortDescReplies(List<OFPortDescStatsReply> portDescReplies) {
        this.ports.addAll(portDescReplies);
    }
    */
    //wenjian
    @Override
    public void setPort(OFPhysicalPort port) {
        this.ports.put(port.getSlotPortId(), port);
    }
    //hdy
    @Override
    public void setPorts(List<OFPhysicalPort> ports) {
        for (OFPhysicalPort port : ports) {
            this.ports.put(port.getSlotPortId(), port); }
    }
    //wenjian

    //hdy
    @Override
    public void setOFTableResource(OFMessage ofTableResource) {
        this.ofTableTypeOFTableResourceMap = ((OFFlowTableResource) ofTableResource).getTableResourcesMap();
    }

    @Override
    public Map<OFTableType, OFTableResource> getOFTableResourceMap() {
        return this.ofTableTypeOFTableResourceMap;
    }

    @Override
    public void returnRoleReply(RoleState requested, RoleState response) {
        this.agent.returnRoleReply(dpid, requested, response);
    }

    @Override
    public abstract void startDriverHandshake();

    @Override
    public abstract boolean isDriverHandshakeComplete();

    @Override
    public abstract void processDriverHandshakeMessage(OFMessage m);


    // Role Handling

    @Override
    public void setRole(RoleState role) {
        try {
            if (role == RoleState.SLAVE || role == RoleState.EQUAL) {
                // perform role transition to SLAVE/EQUAL before sending role request
                this.role = role;
            }
            if (this.roleMan.sendRoleRequest(role, RoleRecvStatus.MATCHED_SET_ROLE)) {
                log.debug("Sending role {} to switch {}", role, getStringId());
                if (role == RoleState.MASTER) {
                    synchronized (messagesPendingMastership) {
                        if (messagesPendingMastership.get() == null) {
                            log.debug("Initializing new message queue for switch {}", dpid);
                            /*
                               The presence of messagesPendingMastership indicates that
                               a switch is currently transitioning to MASTER, but
                               is still awaiting role reply from switch.
                            */
                            messagesPendingMastership.set(Lists.newArrayList());
                        }
                    }
                }
            } else if (role == RoleState.MASTER) {
                // role request not support; transition switch to MASTER
                this.role = role;
            }
        } catch (IOException e) {
            log.error("Unable to write to switch {}.", this.dpid);
        }
    }

    @Override
    public void reassertRole() {
        // TODO should messages be sent directly or queue during reassertion?
        if (this.getRole() == RoleState.MASTER) {
            log.warn("Received permission error from switch {} while " +
                             "being master. Reasserting master role.",
                     this.getStringId());
            this.setRole(RoleState.MASTER);
        }
    }

    @Override
    public void handleRole(OFMessage m) throws SwitchStateException {
//        edited by hdy

        log.info("+++++before transitionToMasterSwitch");
        this.transitionToMasterSwitch();
        RoleReplyInfo rri = roleMan.extractOFRoleReply((OFRoleReply) m);
        RoleRecvStatus rrs = roleMan.deliverRoleReply(rri);
        if (rrs == RoleRecvStatus.MATCHED_SET_ROLE) {
            if (rri.getRole() == RoleState.MASTER) {
                this.transitionToMasterSwitch();
            } else if (rri.getRole() == RoleState.EQUAL ||
                    rri.getRole() == RoleState.SLAVE) {
                this.transitionToEqualSwitch();
            }
        }  else {
            log.warn("Failed to set role for {}", this.getStringId());
        }
    }

    @Override
    public void handleNiciraRole(OFMessage m) throws SwitchStateException {}

    @Override
    public boolean handleRoleError(OFError error) {
        try {
            return RoleRecvStatus.OTHER_EXPECTATION != this.roleMan.deliverError(error);
        } catch (SwitchStateException e) {
            this.disconnectSwitch();
        }
        return true;
    }

    @Override
    public final void setAgent(PofAgent ag) {
        if (this.agent == null) {
            this.agent = ag;
        }
    }

    @Override
    public final void setRoleHandler(RoleHandler roleHandler) {
        if (this.roleMan == null) {
            this.roleMan = roleHandler;
        }
    }

    /*wenjian
    @Override
    public void setSwitchDescription(OFDescStatsReply d) {
        this.desc = d;
    }
    */
    //wenjian

    @Override
    public void setSwitchProperties(OFDescriptionStatistics deSc) {
        this.desc = deSc;
    }

    @Override
    public int getNextTransactionId() {
        return this.xidCounter.getAndIncrement();
    }

    /*wenjian
    @Override
    public List<OFPortDesc> getPorts() {
        return this.ports.stream()
                  .flatMap(portReply -> portReply.getEntries().stream())
                  .collect(Collectors.toList());
    }
    */
    //wenjian
    @Override
    public Map<Integer, OFPhysicalPort> getPorts() {
        return ports;
    }

    @Override
    public String manufacturerDescription() {
        return this.desc.getManufacturerDescription();
    }

    @Override
    public String datapathDescription() {
        return this.desc.getDatapathDescription();
    }

    @Override
    public String hardwareDescription() {
        return this.desc.getHardwareDescription();
    }

    @Override
    public String softwareDescription() {
        return this.desc.getSoftwareDescription();
    }

    @Override
    public String serialNumber() {
        return this.desc.getSerialNumber();
    }

    @Override
    public Device.Type deviceType() {
        return Device.Type.SWITCH;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " [" + ((channel != null)
                ? channel.getRemoteAddress() : "?")
                + " DPID[" + ((getStringId() != null) ? getStringId() : "?") + "]]";
    }

    /**
     * OpenFlow message handler for outgoing control messages.
     */
    protected final class OFMessageHandler implements Runnable {

        protected final OFMessage msg;
        protected final Dpid dpid;

        public OFMessageHandler(Dpid dpid, OFMessage msg) {
            this.msg = msg;
            this.dpid = dpid;
        }

        @Override
        public void run() {
            for (PofEventListener listener : pofOutgoingMsgListener) {
                listener.handleMessage(dpid, msg);
            }
        }
    }
}
