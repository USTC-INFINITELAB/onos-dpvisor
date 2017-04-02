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
package org.onosproject.provider.pof.packet.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.floodlightpof.protocol.OFPacketOut;
import org.onosproject.floodlightpof.protocol.OFType;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionApplyActions;
import org.onosproject.floodlightpof.protocol.action.OFActionOutput;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.instructions.DefaultPofInstructions;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.packet.DefaultInboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketProvider;
import org.onosproject.net.packet.PacketProviderRegistry;
import org.onosproject.net.packet.PacketProviderService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.pof.controller.Dpid;
import org.onosproject.pof.controller.PacketListener;
import org.onosproject.pof.controller.PofController;
import org.onosproject.pof.controller.PofPacketContext;
import org.onosproject.pof.controller.PofSwitch;
import org.onosproject.net.flow.instructions.Instructions.OutputInstruction;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;


/**
 * Provider which uses an OpenFlow controller to detect network
 * infrastructure links.
 */
@Component(immediate = true)
public class PofPacketProvider extends AbstractProvider implements PacketProvider {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketProviderRegistry providerRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PofController controller;

    private PacketProviderService providerService;

    private final InternalPacketProvider listener = new InternalPacketProvider();

    /**
     * Creates an OpenFlow link provider.
     */
    public PofPacketProvider() {
        super(new ProviderId("pof", "org.onosproject.provider.pof"));
    }

    @Activate
    public void activate() {
        providerService = providerRegistry.register(this);
        controller.addPacketListener(20, listener);
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        providerRegistry.unregister(this);
        controller.removePacketListener(listener);
        providerService = null;
        log.info("Stopped");
    }

    @Override
    public void emit(OutboundPacket packet) {
        DeviceId devId = packet.sendThrough();
        String scheme = devId.toString().split(":")[0];

        if (!scheme.equals(this.id().scheme())) {
            throw new IllegalArgumentException(
                    "Don't know how to handle Device with scheme " + scheme);
        }

        Dpid dpid = Dpid.dpid(devId.uri());
        PofSwitch sw = controller.getSwitch(dpid);
        if (sw == null) {
            log.warn("Device {} isn't available?", devId);
            return;
        }
        DefaultPofInstructions.PofInstructionApplyActions pofInstructionApplyActions;
        List<OFAction> actionList = new ArrayList<OFAction>();
        for (Instruction inst : packet.treatment().allInstructions()) {
            switch (inst.type()) {
                case POFINSTRUCTION: {
                    pofInstructionApplyActions = (DefaultPofInstructions.PofInstructionApplyActions) inst;
                    OFInstructionApplyActions insApplyActions = (OFInstructionApplyActions)
                            pofInstructionApplyActions.instruction();
                    actionList.addAll(insApplyActions.getActionList());
                    break; //for now...
                }
                case OUTPUT:
                    long p = ((OutputInstruction) inst).port().toLong();
                    OFActionOutput actionOutput = new OFActionOutput();
                    actionOutput.setPortId((int) p);
                    actionOutput.setPordIdValueType((byte) 0);
                    actionOutput.setMetadataOffset((short) 0);
                    actionOutput.setMetadataLength((short) 0);
                    actionOutput.setPacketOffset((short) 0);
                    actionList.add(actionOutput);
                    break;
                default:
                    break;
            }
        }
        sw.sendMsg(packetOut(sw, packet.data().array(), actionList));
    }

    private OFPacketOut packetOut(PofSwitch sw, byte[] eth, List<OFAction> actionList) {

        OFPacketOut pktout = (OFPacketOut) sw.factory().getOFMessage(OFType.PACKET_OUT);
        pktout.setLength((short) 2360);
        pktout.setType(OFType.PACKET_OUT);
        pktout.setBufferId(-1);
        pktout.setInPort(65535)
                .setActionFactory(sw.factory());
        pktout.setActionsLength((short) actionList.size());
        pktout.setActions(actionList);
        pktout.setPacketData(eth);
        return pktout;
    }

    /**
     * Internal Packet Provider implementation.
     *
     */
    private class InternalPacketProvider implements PacketListener {

        @Override
        public void handlePacket(PofPacketContext pktCtx) {
            DeviceId id = DeviceId.deviceId(Dpid.uri(pktCtx.dpid().value()));
            DefaultInboundPacket inPkt = new DefaultInboundPacket(
                    new ConnectPoint(id, PortNumber.portNumber(pktCtx.inPort())),
                    pktCtx.parsed(), ByteBuffer.wrap(pktCtx.unparsed()),
                    pktCtx.cookie());

            DefaultOutboundPacket outPkt = null;
            if (!pktCtx.isBuffered()) {
                outPkt = new DefaultOutboundPacket(id, null,
                        ByteBuffer.wrap(pktCtx.unparsed()));
            }

            PofCorePacketContext corePktCtx =
                    new PofCorePacketContext(System.currentTimeMillis(),
                            inPkt, outPkt, pktCtx.isHandled(), pktCtx);
            providerService.processPacket(corePktCtx);
        }

    }


}
