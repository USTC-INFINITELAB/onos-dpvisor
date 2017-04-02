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

import org.onlab.packet.DeserializationException;
import org.onlab.packet.Ethernet;
import org.onosproject.floodlightpof.protocol.OFPort;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionApplyActions;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.instructions.DefaultPofInstructions;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instruction.Type;
import org.onosproject.net.packet.DefaultPacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.pof.controller.PofPacketContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Packet context used with the POF providers.
 */
public class PofCorePacketContext extends DefaultPacketContext {

    private static final Logger log = LoggerFactory.getLogger(PofCorePacketContext.class);

    private final PofPacketContext ofPktCtx;

    /**
     * Creates a new POF core packet context.
     *
     * @param time creation time
     * @param inPkt inbound packet
     * @param outPkt outbound packet
     * @param block whether the context is blocked or not
     * @param ofPktCtx OpenFlow packet context
     */
    protected PofCorePacketContext(long time, InboundPacket inPkt,
                                   OutboundPacket outPkt, boolean block,
                                   PofPacketContext ofPktCtx) {
        super(time, inPkt, outPkt, block);
        this.ofPktCtx = ofPktCtx;
    }

    @Override
    public void send() {
        if (!this.block()) {
            if (outPacket() == null) {
                sendPacket(null);
            } else {
                try {
                    Ethernet eth = Ethernet.deserializer()
                            .deserialize(outPacket().data().array(), 0,
                                         outPacket().data().array().length);
                    sendPacket(eth);
                } catch (DeserializationException e) {
                    log.warn("Unable to deserialize packet");
                }
            }
        }
    }

    private void sendPacket(Ethernet eth) {
        List<Instruction> ins = treatmentBuilder().build().allInstructions();
        DefaultPofInstructions.PofInstructionApplyActions pofInstructionApplyActions;
        List<OFAction> actionList = null;
        //TODO: support arbitrary list of treatments must be supported in ofPacketContext
        for (Instruction i : ins) {
            if (i.type() == Type.POFINSTRUCTION) {
                pofInstructionApplyActions = (DefaultPofInstructions.PofInstructionApplyActions) i;

                OFInstructionApplyActions insApplyActions = (OFInstructionApplyActions)
                        pofInstructionApplyActions.instruction();
                actionList = insApplyActions.getActionList();
                break; //for now...
            }
        }
        if (eth == null) {
            ofPktCtx.build(actionList);
        } else {
            ofPktCtx.build(eth, actionList);
        }
        ofPktCtx.send();
    }

    /*    static final long IN_PORT_NUMBER = -8L;
    static final long TABLE_NUMBER = -7L;
    static final long NORMAL_NUMBER = -6L;
    static final long FLOOD_NUMBER = -5L;
    static final long ALL_NUMBER = -4L;
    static final long CONTROLLER_NUMBER = -3L;
    static final long LOCAL_NUMBER = -2L;
    static final long ANY_NUMBER = -1L;*/
    private OFPort buildPort(PortNumber port) {
        switch ((int) port.toLong()) {
            case -8: return OFPort.OFPP_IN_PORT;
            case -7: return OFPort.OFPP_TABLE;
            case -6: return OFPort.OFPP_NORMAL;
            case -5: return OFPort.OFPP_FLOOD;
            case -4: return OFPort.OFPP_ALL;
            case -3: return OFPort.OFPP_CONTROLLER;
            case -2: return OFPort.OFPP_LOCAL;
            case -1: return OFPort.OFPP_ANY;
            default:
                return OFPort.OFPP_MAX;

        }
    }

}
