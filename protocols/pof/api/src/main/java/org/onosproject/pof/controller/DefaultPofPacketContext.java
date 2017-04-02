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
package org.onosproject.pof.controller;

import org.onlab.packet.DeserializationException;
import org.onlab.packet.Ethernet;
import org.onosproject.floodlightpof.protocol.OFPacketIn;
import org.onosproject.floodlightpof.protocol.OFPacketOut;
import org.onosproject.floodlightpof.protocol.OFType;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.action.OFActionOutput;
import org.onosproject.floodlightpof.protocol.action.OFActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferUnderflowException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.onosproject.security.AppGuard.checkPermission;
import static org.onosproject.security.AppPermission.Type.PACKET_READ;
import static org.onosproject.security.AppPermission.Type.PACKET_WRITE;


/**
 * Default implementation of an OpenFlowPacketContext.
 */
public final class DefaultPofPacketContext implements PofPacketContext {

    private final AtomicBoolean free = new AtomicBoolean(true);
    private final AtomicBoolean isBuilt = new AtomicBoolean(false);
    private final PofSwitch sw;
    private final OFPacketIn pktin;
    private OFPacketOut pktout = null;

    private final boolean isBuffered;

    private static final Logger log = LoggerFactory.getLogger(DefaultPofPacketContext.class);


    private DefaultPofPacketContext(PofSwitch s, OFPacketIn pkt) {
        this.sw = s;
        this.pktin = pkt;
        this.isBuffered = pkt.getBufferId() != -1;
    }

    @Override
    public void send() {
        checkPermission(PACKET_WRITE);
        if (block() && isBuilt.get()) {
            sw.sendMsg(pktout);
        }
    }

    @Override
    public void build(List<OFAction> actionList) {
        if (isBuilt.getAndSet(true)) {
            return;
        }
        pktout = (OFPacketOut) sw.factory().getOFMessage(OFType.PACKET_OUT);
        pktout.setXid(pktin.getXid());
        pktout.setLength((short) 2360);
        pktout.setType(OFType.PACKET_OUT);
        pktout.setBufferId(-1);
        pktout.setInPort(65535)
                .setActionFactory(sw.factory());
        pktout.setActionsLength((short) actionList.size());
        pktout.setActions(actionList);
        pktout.setPacketData(pktin.getPacketData());
    }

    @Override
    public void build(Ethernet ethFrame, List<OFAction> actionList) {
        if (isBuilt.getAndSet(true)) {
            return;
        }
        pktout = (OFPacketOut) sw.factory().getOFMessage(OFType.PACKET_OUT);
        pktout.setXid(pktin.getXid());
        pktout.setLength((short) 2360);
        pktout.setType(OFType.PACKET_OUT);
        pktout.setBufferId(-1);
        pktout.setInPort(65535)
        .setActionFactory(sw.factory());
        pktout.setActionsLength((short) actionList.size());
        pktout.setActions(actionList);
        pktout.setPacketData(ethFrame.serialize());
    }

    @Override
    public Ethernet parsed() {
        checkPermission(PACKET_READ);

        try {
            return Ethernet.deserializer().deserialize(pktin.getPacketData(), 0, pktin.getPacketData().length);
        } catch (BufferUnderflowException | NullPointerException |
                DeserializationException e) {
            Logger loG = LoggerFactory.getLogger(getClass());
            loG.error("packet deserialization problem : {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Dpid dpid() {
        checkPermission(PACKET_READ);

        return new Dpid(sw.getId());
    }

    /**
     * Creates an OpenFlow packet context based on a packet-in.
     *
     * @param s OpenFlow switch
     * @param pkt OpenFlow packet-in
     * @return the OpenFlow packet context
     */
    public static PofPacketContext packetContextFromPacketIn(PofSwitch s,
                                                                  OFPacketIn pkt) {
        return new DefaultPofPacketContext(s, pkt);
    }

    @Override
    public Integer inPort() {
        checkPermission(PACKET_READ);

        return pktinInPort();
    }

    private int pktinInPort() {
        return pktin.getPortId();
    }

    @Override
    public byte[] unparsed() {
        checkPermission(PACKET_READ);

        return pktin.getPacketData().clone();

    }

    private OFActionOutput buildOutput(Integer port) {
        OFActionOutput act = (OFActionOutput) sw.factory().getAction(OFActionType.OUTPUT);
        act.setPortId(port);
        return act;
    }

    @Override
    public boolean block() {
        checkPermission(PACKET_WRITE);

        return free.getAndSet(false);
    }

    @Override
    public boolean isHandled() {
        checkPermission(PACKET_READ);

        return !free.get();
    }

    @Override
    public boolean isBuffered() {
        checkPermission(PACKET_READ);

        return isBuffered;
    }

    @Override
    public Optional<Long> cookie() {
        checkPermission(PACKET_READ);
        /*wenjian
        if (pktin.getVersion() != OFVersion.OF_10) {
            return Optional.of(pktin.getCookie().getValue());
        } else {
            return Optional.empty();
        }
        */
        return Optional.empty();
    }

}
