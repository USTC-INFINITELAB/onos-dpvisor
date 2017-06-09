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

package org.onosproject.incubator.net.virtual.impl.provider;

import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.action.OFActionOutput;
import org.onosproject.floodlightpof.protocol.instruction.OFInstruction;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionApplyActions;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.incubator.net.virtual.VirtualNetworkService;
import org.onosproject.incubator.net.virtual.VirtualPacketContext;
import org.onosproject.incubator.net.virtual.VirtualPort;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.DefaultPofActions;
import org.onosproject.net.flow.instructions.DefaultPofInstructions;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.PofInstruction;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.DefaultPacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Represents context for processing an inbound packet for a virtual network
 * and (optionally) emitting a corresponding outbound packet.
 */
public class DefaultVirtualPofPacketContext extends DefaultPacketContext
        implements VirtualPacketContext {

    private final Logger log = getLogger(getClass());

    private NetworkId networkId;
    private PacketService packetService;
    private VirtualNetworkService vnService;

    /**
     * Creates a new packet context.
     *
     * @param time   creation time
     * @param inPkt  inbound packet
     * @param outPkt outbound packet
     * @param block  whether the context is blocked or not
     * @param networkId virtual network ID where this context is handled
     * @param packetService  core packet service
     * @param vnService virtual network service
     */

    protected DefaultVirtualPofPacketContext(long time, InboundPacket inPkt,
                                             OutboundPacket outPkt, boolean block,
                                             NetworkId networkId,
                                             PacketService packetService,
                                             VirtualNetworkService vnService) {
        super(time, inPkt, outPkt, block);

        this.networkId = networkId;
        this.packetService = packetService;
        this.vnService = vnService;
    }

    @Override
    public void send() {
        if (!this.block()) {
            devirtualizeContext(this)
                    .forEach(outboundPacket -> packetService.emit(outboundPacket));
        }
    }

    public NetworkId getNetworkId() {
        return networkId;
    }

    /**
     * Translate the requested a virtual Packet Context into
     * a set physical outbound packets.
     * This method is designed to support Context's send() method that invoked
     * by applications.
     * See {@link org.onosproject.net.packet.PacketContext}
     *
     * @param context A handled packet context
     */
    private Set<OutboundPacket> devirtualizeContext(DefaultVirtualPofPacketContext context) {

        Set<OutboundPacket> outboundPackets = new HashSet<>();

        NetworkId networkId = context.getNetworkId();
        TrafficTreatment vTreatment = context.treatmentBuilder().build();
        DeviceId sendThrough = context.outPacket().sendThrough();

        Set<VirtualPort> vPorts = vnService
                .getVirtualPorts(networkId, sendThrough);

        List<Instruction> insList = vTreatment.allInstructions();

        List<OFAction> ofActionList = new LinkedList<>();
        List<PofInstruction> pofInsList = new LinkedList<>();

        for (Instruction ins : insList) {
            if (ins.type() == Instruction.Type.POFINSTRUCTION) {
                PofInstruction pofInstruction = (PofInstruction) ins;
                switch (pofInstruction.pofInstructionType()) {
                    case POF_ACTION:
                        DefaultPofInstructions.PofInstructionApplyActions pofInstructionApplyActions
                                = (DefaultPofInstructions.PofInstructionApplyActions) pofInstruction;
                        OFInstruction ofInstruction = pofInstructionApplyActions.instruction();
                        OFInstructionApplyActions ofInstructionApplyActions
                                = (OFInstructionApplyActions) ofInstruction;
                        ofActionList = ofInstructionApplyActions.getActionList();
                        break;
                    case CALCULATE_FIELD:
                    case GOTO_DIRECT_TABLE:
                    case GOTO_TABLE:
                    case WRITE_METADATA:
                    case WRITE_METADATA_FROM_PACKET:
                        pofInsList.add(pofInstruction);
                        break;
                    default:
                        log.warn("Pof instruction type {} not yet implemented.", pofInstruction.pofInstructionType());
                }
            }
        }

        OFActionOutput ofActionOutput = null;
        for (OFAction oa : ofActionList) {
            if (oa instanceof OFActionOutput) {
                ofActionOutput = (OFActionOutput) oa;
                break;
            }
        }

        if (ofActionOutput != null) {
            int portId = ofActionOutput.getPortId();
            PortNumber vOutPortNum = PortNumber.portNumber(portId);
            if (ofActionList.remove(ofActionOutput)) {
                Optional<ConnectPoint> optionalCpOut = vPorts.stream()
                        .filter(v -> v.number().equals(vOutPortNum))
                        .map(v -> v.realizedBy())
                        .findFirst();
                if (!optionalCpOut.isPresent()) {
                    log.warn("Port {} is not realized yet, in Network {}, Device {}",
                             vOutPortNum, networkId, sendThrough);
                    return outboundPackets;
                }

                ConnectPoint egressPoint = optionalCpOut.get();
                short ouPortId = (short) egressPoint.port().toLong();
                ofActionList.add(DefaultPofActions.output((short)0, (short)0, (short)0, ouPortId).action());

                TrafficTreatment.Builder ttBuilder = DefaultTrafficTreatment.builder();
                ttBuilder.add(DefaultPofInstructions.applyActions(ofActionList));
                for (PofInstruction pi : pofInsList) {
                    ttBuilder.add(pi);
                }

                OutboundPacket outboundPacket = new DefaultOutboundPacket(
                        egressPoint.deviceId(), ttBuilder.build(), context.outPacket().data());

                outboundPackets.add(outboundPacket);

            } else {
                log.error("Remove OFAction error!");
                return outboundPackets;
            }

        }

        return outboundPackets;
    }

    @Override
    public NetworkId networkId() {
        return networkId;
    }
}
