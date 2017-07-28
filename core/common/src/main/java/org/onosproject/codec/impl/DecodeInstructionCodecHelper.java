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
package org.onosproject.codec.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.EthType;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.MplsLabel;
import org.onlab.packet.TpPort;
import org.onlab.packet.VlanId;
import org.onlab.util.HexString;
import org.onosproject.codec.CodecContext;
import org.onosproject.net.flow.ExtensionTreatmentCodec;
import org.onosproject.core.GroupId;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionCalculateField;
import org.onosproject.net.ChannelSpacing;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.GridType;
import org.onosproject.net.OchSignal;
import org.onosproject.net.OduSignalId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.instructions.*;
import org.onosproject.net.meter.MeterId;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.onlab.util.Tools.nullIsIllegal;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Decoding portion of the instruction codec.
 */
public final class DecodeInstructionCodecHelper {
    protected static final Logger log = getLogger(DecodeInstructionCodecHelper.class);
    private final ObjectNode json;
    private final CodecContext context;
    private static final Pattern ETHTYPE_PATTERN = Pattern.compile("0x([0-9a-fA-F]{4})");

    /**
     * Creates a decode instruction codec object.
     *
     * @param json JSON object to decode
     * @param context codec context
     */
    public DecodeInstructionCodecHelper(ObjectNode json, CodecContext context) {
        this.json = json;
        this.context = context;
    }

    /**
     * Decodes a Layer 2 instruction.
     *
     * @return instruction object decoded from the JSON
     * @throws IllegalArgumentException if the JSON is invalid
     */
    private Instruction decodeL2() {
        String subType = nullIsIllegal(json.get(InstructionCodec.SUBTYPE),
                InstructionCodec.SUBTYPE + InstructionCodec.ERROR_MESSAGE).asText();

        if (subType.equals(L2ModificationInstruction.L2SubType.ETH_SRC.name())) {
            String mac = nullIsIllegal(json.get(InstructionCodec.MAC),
                    InstructionCodec.MAC + InstructionCodec.MISSING_MEMBER_MESSAGE).asText();
            return Instructions.modL2Src(MacAddress.valueOf(mac));
        } else if (subType.equals(L2ModificationInstruction.L2SubType.ETH_DST.name())) {
            String mac = nullIsIllegal(json.get(InstructionCodec.MAC),
                    InstructionCodec.MAC + InstructionCodec.MISSING_MEMBER_MESSAGE).asText();
            return Instructions.modL2Dst(MacAddress.valueOf(mac));
        } else if (subType.equals(L2ModificationInstruction.L2SubType.VLAN_ID.name())) {
            short vlanId = (short) nullIsIllegal(json.get(InstructionCodec.VLAN_ID),
                    InstructionCodec.VLAN_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return Instructions.modVlanId(VlanId.vlanId(vlanId));
        } else if (subType.equals(L2ModificationInstruction.L2SubType.VLAN_PCP.name())) {
            byte vlanPcp = (byte) nullIsIllegal(json.get(InstructionCodec.VLAN_PCP),
                    InstructionCodec.VLAN_PCP + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return Instructions.modVlanPcp(vlanPcp);
        } else if (subType.equals(L2ModificationInstruction.L2SubType.MPLS_LABEL.name())) {
            int label = nullIsIllegal(json.get(InstructionCodec.MPLS_LABEL),
                    InstructionCodec.MPLS_LABEL + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return Instructions.modMplsLabel(MplsLabel.mplsLabel(label));
        } else if (subType.equals(L2ModificationInstruction.L2SubType.MPLS_PUSH.name())) {
            return Instructions.pushMpls();
        } else if (subType.equals(L2ModificationInstruction.L2SubType.MPLS_POP.name())) {
            if (json.has(InstructionCodec.ETHERNET_TYPE)) {
                return Instructions.popMpls(getEthType());
            }
            return Instructions.popMpls();
        } else if (subType.equals(L2ModificationInstruction.L2SubType.DEC_MPLS_TTL.name())) {
            return Instructions.decMplsTtl();
        } else if (subType.equals(L2ModificationInstruction.L2SubType.VLAN_POP.name())) {
            return Instructions.popVlan();
        } else if (subType.equals(L2ModificationInstruction.L2SubType.VLAN_PUSH.name())) {
            if (json.has(InstructionCodec.ETHERNET_TYPE)) {
                return Instructions.pushVlan(getEthType());
            }
            return Instructions.pushVlan();
        } else if (subType.equals(L2ModificationInstruction.L2SubType.TUNNEL_ID.name())) {
            long tunnelId = nullIsIllegal(json.get(InstructionCodec.TUNNEL_ID),
                    InstructionCodec.TUNNEL_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asLong();
            return Instructions.modTunnelId(tunnelId);
        }
        throw new IllegalArgumentException("L2 Instruction subtype "
                + subType + " is not supported");
    }

    /**
     * Decodes a Layer 3 instruction.
     *
     * @return instruction object decoded from the JSON
     * @throws IllegalArgumentException if the JSON is invalid
     */
    private Instruction decodeL3() {
        String subType = nullIsIllegal(json.get(InstructionCodec.SUBTYPE),
                InstructionCodec.SUBTYPE + InstructionCodec.ERROR_MESSAGE).asText();

        if (subType.equals(L3ModificationInstruction.L3SubType.IPV4_SRC.name())) {
            IpAddress ip = IpAddress.valueOf(nullIsIllegal(json.get(InstructionCodec.IP),
                    InstructionCodec.IP + InstructionCodec.MISSING_MEMBER_MESSAGE).asText());
            return Instructions.modL3Src(ip);
        } else if (subType.equals(L3ModificationInstruction.L3SubType.IPV4_DST.name())) {
            IpAddress ip = IpAddress.valueOf(nullIsIllegal(json.get(InstructionCodec.IP),
                    InstructionCodec.IP + InstructionCodec.MISSING_MEMBER_MESSAGE).asText());
            return Instructions.modL3Dst(ip);
        } else if (subType.equals(L3ModificationInstruction.L3SubType.IPV6_SRC.name())) {
            IpAddress ip = IpAddress.valueOf(nullIsIllegal(json.get(InstructionCodec.IP),
                    InstructionCodec.IP + InstructionCodec.MISSING_MEMBER_MESSAGE).asText());
            return Instructions.modL3IPv6Src(ip);
        } else if (subType.equals(L3ModificationInstruction.L3SubType.IPV6_DST.name())) {
            IpAddress ip = IpAddress.valueOf(nullIsIllegal(json.get(InstructionCodec.IP),
                    InstructionCodec.IP + InstructionCodec.MISSING_MEMBER_MESSAGE).asText());
            return Instructions.modL3IPv6Dst(ip);
        } else if (subType.equals(L3ModificationInstruction.L3SubType.IPV6_FLABEL.name())) {
            int flowLabel = nullIsIllegal(json.get(InstructionCodec.FLOW_LABEL),
                    InstructionCodec.FLOW_LABEL + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return Instructions.modL3IPv6FlowLabel(flowLabel);
        } else  if (subType.equals(L3ModificationInstruction.L3SubType.TTL_IN.name())) {
            return Instructions.copyTtlIn();
        } else  if (subType.equals(L3ModificationInstruction.L3SubType.TTL_OUT.name())) {
            return Instructions.copyTtlOut();
        } else  if (subType.equals(L3ModificationInstruction.L3SubType.DEC_TTL.name())) {
            return Instructions.decNwTtl();
        }
        throw new IllegalArgumentException("L3 Instruction subtype "
                + subType + " is not supported");
    }

    /**
     * Decodes a Layer 0 instruction.
     *
     * @return instruction object decoded from the JSON
     * @throws IllegalArgumentException if the JSON is invalid
     */
    private Instruction decodeL0() {
        String subType = nullIsIllegal(json.get(InstructionCodec.SUBTYPE),
                InstructionCodec.SUBTYPE + InstructionCodec.ERROR_MESSAGE).asText();

        if (subType.equals(L0ModificationInstruction.L0SubType.OCH.name())) {
            String gridTypeString = nullIsIllegal(json.get(InstructionCodec.GRID_TYPE),
                    InstructionCodec.GRID_TYPE + InstructionCodec.MISSING_MEMBER_MESSAGE).asText();
            GridType gridType = GridType.valueOf(gridTypeString);
            if (gridType == null) {
                throw new IllegalArgumentException("Unknown grid type  "
                        + gridTypeString);
            }
            String channelSpacingString = nullIsIllegal(json.get(InstructionCodec.CHANNEL_SPACING),
                    InstructionCodec.CHANNEL_SPACING + InstructionCodec.MISSING_MEMBER_MESSAGE).asText();
            ChannelSpacing channelSpacing = ChannelSpacing.valueOf(channelSpacingString);
            if (channelSpacing == null) {
                throw new IllegalArgumentException("Unknown channel spacing  "
                        + channelSpacingString);
            }
            int spacingMultiplier = nullIsIllegal(json.get(InstructionCodec.SPACING_MULTIPLIER),
                    InstructionCodec.SPACING_MULTIPLIER + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            int slotGranularity = nullIsIllegal(json.get(InstructionCodec.SLOT_GRANULARITY),
                    InstructionCodec.SLOT_GRANULARITY + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return Instructions.modL0Lambda(new OchSignal(gridType, channelSpacing,
                    spacingMultiplier, slotGranularity));
        }
        throw new IllegalArgumentException("L0 Instruction subtype "
                + subType + " is not supported");
    }

    /**
     * Decodes a Layer 1 instruction.
     *
     * @return instruction object decoded from the JSON
     * @throws IllegalArgumentException if the JSON is invalid
     */
    private Instruction decodeL1() {
        String subType = nullIsIllegal(json.get(InstructionCodec.SUBTYPE),
                InstructionCodec.SUBTYPE + InstructionCodec.ERROR_MESSAGE).asText();
        if (subType.equals(L1ModificationInstruction.L1SubType.ODU_SIGID.name())) {
            int tributaryPortNumber = nullIsIllegal(json.get(InstructionCodec.TRIBUTARY_PORT_NUMBER),
                    InstructionCodec.TRIBUTARY_PORT_NUMBER + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            int tributarySlotLen = nullIsIllegal(json.get(InstructionCodec.TRIBUTARY_SLOT_LEN),
                    InstructionCodec.TRIBUTARY_SLOT_LEN + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            byte[] tributarySlotBitmap = null;
            tributarySlotBitmap = HexString.fromHexString(
                    nullIsIllegal(json.get(InstructionCodec.TRIBUTARY_SLOT_BITMAP),
                    InstructionCodec.TRIBUTARY_SLOT_BITMAP + InstructionCodec.MISSING_MEMBER_MESSAGE).asText());
            return Instructions.modL1OduSignalId(OduSignalId.oduSignalId(tributaryPortNumber, tributarySlotLen,
                    tributarySlotBitmap));
        }
        throw new IllegalArgumentException("L1 Instruction subtype "
                + subType + " is not supported");
    }

    /**
     * Decodes a Layer 4 instruction.
     *
     * @return instruction object decoded from the JSON
     * @throws IllegalArgumentException if the JSON is invalid
     */
    private Instruction decodeL4() {
        String subType = nullIsIllegal(json.get(InstructionCodec.SUBTYPE),
                InstructionCodec.SUBTYPE + InstructionCodec.ERROR_MESSAGE).asText();

        if (subType.equals(L4ModificationInstruction.L4SubType.TCP_DST.name())) {
            TpPort tcpPort = TpPort.tpPort(nullIsIllegal(json.get(InstructionCodec.TCP_PORT),
                    InstructionCodec.TCP_PORT + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt());
            return Instructions.modTcpDst(tcpPort);
        } else if (subType.equals(L4ModificationInstruction.L4SubType.TCP_SRC.name())) {
            TpPort tcpPort = TpPort.tpPort(nullIsIllegal(json.get(InstructionCodec.TCP_PORT),
                    InstructionCodec.TCP_PORT + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt());
            return Instructions.modTcpSrc(tcpPort);
        } else if (subType.equals(L4ModificationInstruction.L4SubType.UDP_DST.name())) {
            TpPort udpPort = TpPort.tpPort(nullIsIllegal(json.get(InstructionCodec.UDP_PORT),
                    InstructionCodec.UDP_PORT + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt());
            return Instructions.modUdpDst(udpPort);
        } else if (subType.equals(L4ModificationInstruction.L4SubType.UDP_SRC.name())) {
            TpPort udpPort = TpPort.tpPort(nullIsIllegal(json.get(InstructionCodec.UDP_PORT),
                    InstructionCodec.UDP_PORT + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt());
            return Instructions.modUdpSrc(udpPort);
        }
        throw new IllegalArgumentException("L4 Instruction subtype "
                + subType + " is not supported");
    }

    /**
     * Decodes a Pof instruction.
     *
     * @return instruction object decoded from the JSON
     * @throws IllegalArgumentException if the JSON is invalid
     */
    private Instruction decodePof() {
        String node = json.get(InstructionCodec.POF_INSTRUCTION).asText();
        if (node.equals(Instruction.Type.POFACTION.name())) {
            JsonNode pofActionsJson = json.get(InstructionCodec.ACTIONS);
            List<OFAction> actions = new ArrayList<OFAction>();
            IntStream.range(0, pofActionsJson.size())
                    .forEach(i -> actions.add(
                            decodePofAction(pofActionsJson.path(i))));
            return DefaultPofInstructions.applyActions(actions);
        } else if (node.equals(PofInstruction.PofInstructionType.WRITE_METADATA.name())) {
            short metadataOffset = (short) nullIsIllegal(json.get(InstructionCodec.METADATA_OFFSET),
                    InstructionCodec.METADATA_OFFSET + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short writeLength = (short) nullIsIllegal(json.get(InstructionCodec.WRITE_LENGTH),
                    InstructionCodec.WRITE_LENGTH + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            byte[] value =  nullIsIllegal(json.get(InstructionCodec.VALUE),
                    InstructionCodec.VALUE + InstructionCodec.MISSING_MEMBER_MESSAGE).asText().getBytes();
            return DefaultPofInstructions.writeMetadata(metadataOffset, writeLength, value);
        } else if (node.equals(PofInstruction.PofInstructionType.WRITE_METADATA_FROM_PACKET.name())) {
            short metadataOffset = (short) nullIsIllegal(json.get(InstructionCodec.METADATA_OFFSET),
                    InstructionCodec.METADATA_OFFSET + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short metadataLength = (short) nullIsIllegal(json.get(InstructionCodec.METADATA_LENGTH),
                    InstructionCodec.METADATA_LENGTH + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short packetOffset = (short) nullIsIllegal(json.get(InstructionCodec.PACKET_OFFSET),
                    InstructionCodec.PACKET_OFFSET + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofInstructions.writeMetadataFromPacket(metadataOffset, packetOffset, metadataLength);
        } else if (node.equals(PofInstruction.PofInstructionType.CALCULATE_FIELD.name())) {
            JsonNode srcFieldJson = json.get(InstructionCodec.SRC_FIELD);
            JsonNode dstFieldJson = json.get(InstructionCodec.DST_FIELD);
            OFInstructionCalculateField.OFCalcType type = OFInstructionCalculateField.OFCalcType.OFPCT_ADD;
            FlowTableCodec flowTableCodec = new FlowTableCodec();
            String calcType = nullIsIllegal(json.get(InstructionCodec.OF_CALC_TYPE),
                    InstructionCodec.OF_CALC_TYPE + InstructionCodec.MISSING_MEMBER_MESSAGE).asText();
            if (calcType.equals("OFPCT_ADD")) {
                type = OFInstructionCalculateField.OFCalcType.OFPCT_ADD;
            } else if (calcType.equals("OFPCT_SUBTRACT")) {
                type = OFInstructionCalculateField.OFCalcType.OFPCT_SUBTRACT;
            } else if (calcType.equals("OFPCT_LEFT_SHIFT")) {
                type = OFInstructionCalculateField.OFCalcType.OFPCT_LEFT_SHIFT;
            } else if(calcType.equals("OFPCT_RIGHT_SHIFT")) {
                type = OFInstructionCalculateField.OFCalcType.OFPCT_RIGHT_SHIFT;
            } else if (calcType.equals("OFPCT_BITWISE_ADD")) {
                type = OFInstructionCalculateField.OFCalcType.OFPCT_BITWISE_ADD;
            } else if (calcType.equals("OFPCT_BITWISE_OR")) {
                type = OFInstructionCalculateField.OFCalcType.OFPCT_BITWISE_OR;
            } else if (calcType.equals("OFPCT_BITWISE_XOR")) {
                type = OFInstructionCalculateField.OFCalcType.OFPCT_BITWISE_XOR;
            } else if (calcType.equals("OFPCT_BITWISE_NOR")) {
                type = OFInstructionCalculateField.OFCalcType.OFPCT_BITWISE_NOR;
            }
            OFMatch20 srcField = flowTableCodec.match20Codec(srcFieldJson);
            OFMatch20 dstField = flowTableCodec.match20Codec(dstFieldJson);
            byte srcValueType = (byte) nullIsIllegal(json.get(InstructionCodec.SRC_VALUE_TYPE),
                    InstructionCodec.SRC_VALUE_TYPE + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            int srcValue = nullIsIllegal(json.get(InstructionCodec.SRC_VALUE),
                    InstructionCodec.SRC_VALUE + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofInstructions.calculateField(type, srcValueType, dstField, srcValue, srcField);
        } else if (node.equals(PofInstruction.PofInstructionType.GOTO_TABLE.name())) {
            JsonNode matchListJson = json.get(InstructionCodec.MATCH_LIST);
            ArrayList<OFMatch20> matchList = new ArrayList<OFMatch20>();
            FlowTableCodec flowTableCodec = new FlowTableCodec();
            IntStream.range(0, matchListJson.size())
                    .forEach(i -> matchList.add(
                            flowTableCodec.match20Codec(matchListJson.path(i))));
            byte matchFieldNum = (byte) nullIsIllegal(json.get(InstructionCodec.MATCH_FIELD_NUM),
                    InstructionCodec.MATCH_FIELD_NUM + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short packetOffset = (short) nullIsIllegal(json.get(InstructionCodec.PACKET_OFFSET),
                    InstructionCodec.PACKET_OFFSET + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            byte nextTableId = (byte) nullIsIllegal(json.get(InstructionCodec.NEXT_TABLE_ID),
                    InstructionCodec.NEXT_TABLE_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofInstructions.gotoTable(nextTableId, matchFieldNum, packetOffset, matchList);
        } else if (node.equals(PofInstruction.PofInstructionType.GOTO_DIRECT_TABLE.name())) {
            JsonNode matchFieldJson = json.get(InstructionCodec.INDEX_FIELD);
            FlowTableCodec flowTableCodec = new FlowTableCodec();
            OFMatch20 indexField = flowTableCodec.match20Codec(matchFieldJson);
            byte nextTableId = (byte) nullIsIllegal(json.get(InstructionCodec.NEXT_TABLE_ID),
                    InstructionCodec.NEXT_TABLE_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            byte indexType = (byte) nullIsIllegal(json.get(InstructionCodec.INDEX_TYPE),
                    InstructionCodec.INDEX_TYPE + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short packetOffset = (short) nullIsIllegal(json.get(InstructionCodec.PACKET_OFFSET),
                    InstructionCodec.PACKET_OFFSET + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            int indexValue = nullIsIllegal(json.get(InstructionCodec.INDEX_VALUE),
                    InstructionCodec.INDEX_VALUE + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofInstructions.gotoDirectTable(nextTableId, indexType, packetOffset, indexValue, indexField);
        }
        throw new IllegalArgumentException("Pof Instruction node "
                + node + "is not supported");
    }

    private OFAction decodePofAction (JsonNode json) {
        String node = json.get(InstructionCodec.POF_ACTION).asText();
        if (node.equals(DefaultPofActions.PofActionType.OUTPUT.name())) {
            short metadataOffset = (short) nullIsIllegal(json.get(InstructionCodec.METADATA_OFFSET),
                    InstructionCodec.METADATA_OFFSET + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short metadataLength = (short) nullIsIllegal(json.get(InstructionCodec.METADATA_LENGTH),
                    InstructionCodec.METADATA_LENGTH + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short packetOffset = (short) nullIsIllegal(json.get(InstructionCodec.PACKET_OFFSET),
                    InstructionCodec.PACKET_OFFSET + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            int portId = nullIsIllegal(json.get(InstructionCodec.PORT),
                    InstructionCodec.PORT + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofActions.output(metadataOffset, metadataLength, packetOffset, portId).action();
        } else if (node.equals(DefaultPofActions.PofActionType.ADD_FIELD.name())) {
            short fieldId = (short) nullIsIllegal(json.get(InstructionCodec.FIELD_ID),
                    InstructionCodec.FIELD_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short fieldPostion = (short) nullIsIllegal(json.get(InstructionCodec.FIELD_POSITION),
                    InstructionCodec.FIELD_POSITION + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short fieldLength = (short) nullIsIllegal(json.get(InstructionCodec.FIELD_LENGTH),
                    InstructionCodec.FIELD_LENGTH + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            String fieldValue = nullIsIllegal(json.get(InstructionCodec.FIELD_VALUE),
                    InstructionCodec.FIELD_VALUE + InstructionCodec.MISSING_MEMBER_MESSAGE).asText();
            return DefaultPofActions.addField(fieldId, fieldPostion, fieldLength, fieldValue).action();
        } else if (node.equals(DefaultPofActions.PofActionType.CALCULATE_CHECKSUM.name())) {
            byte checksumPosType = (byte) nullIsIllegal(json.get(InstructionCodec.CHECKSUM_POS_TYPE),
                    InstructionCodec.CHECKSUM_POS_TYPE + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            byte calcPosType = (byte) nullIsIllegal(json.get(InstructionCodec.CALC_POS_TYPE),
                    InstructionCodec.CALC_POS_TYPE + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short checksumPosition = (short) nullIsIllegal(json.get(InstructionCodec.CHECKSUM_POSITION),
                    InstructionCodec.CHECKSUM_POSITION + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short checksumLength = (short) nullIsIllegal(json.get(InstructionCodec.CHECKSUM_LENGTH),
                    InstructionCodec.CHECKSUM_LENGTH + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short calcStartPosition = (short) nullIsIllegal(json.get(InstructionCodec.CALC_START_POSITION),
                    InstructionCodec.CALC_START_POSITION + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short calcLength = (short) nullIsIllegal(json.get(InstructionCodec.CALC_LENGTH),
                    InstructionCodec.CALC_LENGTH + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofActions
                    .calcCheckSum(checksumPosType, calcPosType, checksumPosition
                            , checksumLength, calcStartPosition, calcLength).action();
        } else if (node.equals(DefaultPofActions.PofActionType.COUNTER.name())) {
            short counterId = (short) nullIsIllegal(json.get(InstructionCodec.COUNTER_ID),
                    InstructionCodec.COUNTER_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofActions.counter(counterId).action();
        } else if (node.equals(DefaultPofActions.PofActionType.DELETE_FIELD.name())) {
            short tagPosition = (short) nullIsIllegal(json.get(InstructionCodec.TAG_POSITION),
                    InstructionCodec.TAG_POSITION + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            short tagLengthValue = (short) nullIsIllegal(json.get(InstructionCodec.TAG_LENGTH_VALUE),
                    InstructionCodec.TAG_LENGTH_VALUE + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofActions.deleteField(tagPosition, tagLengthValue).action();
        } else if (node.equals(DefaultPofActions.PofActionType.DROP.name())) {
            short reason = (short) nullIsIllegal(json.get(InstructionCodec.REASON),
                    InstructionCodec.REASON + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofActions.drop(reason).action();
        } else if (node.equals(DefaultPofActions.PofActionType.GROUP.name())) {
            short groupId = (short) nullIsIllegal(json.get(InstructionCodec.GROUP_ID),
                    InstructionCodec.GROUP_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofActions.group(groupId).action();
        } else if (node.equals(DefaultPofActions.PofActionType.MODIFY_FIELD.name())) {
            JsonNode matchFieldJson = json.get(InstructionCodec.MATCH_FIELD);
            FlowTableCodec flowTableCodec = new FlowTableCodec();
            OFMatch20 matchField = flowTableCodec.match20Codec(matchFieldJson);
            short increment = (short) nullIsIllegal(json.get(InstructionCodec.INCREMENT),
                    InstructionCodec.INCREMENT + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofActions.modifyField(matchField, increment).action();
        } else if(node.equals(DefaultPofActions.PofActionType.PACKET_IN.name())) {
            short reason = (short) nullIsIllegal(json.get(InstructionCodec.REASON),
                    InstructionCodec.REASON + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofActions.packetIn(reason).action();
        } else if (node.equals(DefaultPofActions.PofActionType.SET_FIELD.name())) {
            JsonNode matchFieldJson = json.get(InstructionCodec.MATCH_FIELD);
            FlowTableCodec flowTableCodec = new FlowTableCodec();
            OFMatch20 matchField = flowTableCodec.match20Codec(matchFieldJson);
            String value = nullIsIllegal(json.get(InstructionCodec.VALUE),
                    InstructionCodec.VALUE + InstructionCodec.MISSING_MEMBER_MESSAGE).asText();
            String mask = nullIsIllegal(json.get(InstructionCodec.MASK),
                    InstructionCodec.MASK + InstructionCodec.MISSING_MEMBER_MESSAGE).asText();
            return DefaultPofActions.setField(matchField, value, mask).action();
        } else if (node.equals(DefaultPofActions.PofActionType.SET_FIELD_FROM_METADATA.name())) {
            JsonNode matchFieldJson = json.get(InstructionCodec.MATCH_FIELD);
            FlowTableCodec flowTableCodec = new FlowTableCodec();
            OFMatch20 matchField = flowTableCodec.match20Codec(matchFieldJson);
            short metadataOffset = (short) nullIsIllegal(json.get(InstructionCodec.METADATA_OFFSET),
                    InstructionCodec.METADATA_OFFSET + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt();
            return DefaultPofActions.setFieldFromMetadata(matchField, metadataOffset).action();
        }
        throw new IllegalArgumentException("PofAction node "
                + node + "is not supported");
    }

    /**
     * Decodes a extension instruction.
     *
     * @return extension treatment
     */
    private Instruction decodeExtension() {
        ObjectNode node = (ObjectNode) json.get(InstructionCodec.EXTENSION);
        if (node != null) {
            DeviceId deviceId = getDeviceId();

            ServiceDirectory serviceDirectory = new DefaultServiceDirectory();
            DeviceService deviceService = serviceDirectory.get(DeviceService.class);
            Device device = deviceService.getDevice(deviceId);

            if (device == null) {
                throw new IllegalArgumentException("Device not found");
            }

            if (device.is(ExtensionTreatmentCodec.class)) {
                ExtensionTreatmentCodec treatmentCodec = device.as(ExtensionTreatmentCodec.class);
                ExtensionTreatment treatment = treatmentCodec.decode(node, context);
                return Instructions.extension(treatment, deviceId);
            } else {
                throw new IllegalArgumentException(
                        "There is no codec to decode extension for device " + deviceId.toString());
            }
        }
        return null;
    }

    /**
     * Returns device identifier.
     *
     * @return device identifier
     * @throws IllegalArgumentException if the JSON is invalid
     */
    private DeviceId getDeviceId() {
        JsonNode deviceIdNode = json.get(InstructionCodec.DEVICE_ID);
        if (deviceIdNode != null) {
            return DeviceId.deviceId(deviceIdNode.asText());
        }
        throw new IllegalArgumentException("Empty device identifier");
    }

    /**
     * Extracts port number of the given json node.
     *
     * @param jsonNode json node
     * @return port number
     */
    private PortNumber getPortNumber(ObjectNode jsonNode) {
        PortNumber portNumber;
        JsonNode portNode = nullIsIllegal(jsonNode.get(InstructionCodec.PORT),
                InstructionCodec.PORT + InstructionCodec.ERROR_MESSAGE);
        if (portNode.isLong() || portNode.isInt()) {
            portNumber = PortNumber.portNumber(portNode.asLong());
        } else if (portNode.isTextual()) {
            portNumber = PortNumber.fromString(portNode.textValue());
        } else {
            throw new IllegalArgumentException("Port value "
                    + portNode.toString()
                    + " is not supported");
        }
        return portNumber;
    }

    /**
     * Returns Ethernet type.
     *
     * @return ethernet type
     * @throws IllegalArgumentException if the JSON is invalid
     */
    private EthType getEthType() {
        String ethTypeStr = nullIsIllegal(json.get(InstructionCodec.ETHERNET_TYPE),
                  InstructionCodec.ETHERNET_TYPE + InstructionCodec.MISSING_MEMBER_MESSAGE).asText();
        Matcher matcher = ETHTYPE_PATTERN.matcher(ethTypeStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("ETHERNET_TYPE must be a four digit hex string starting with 0x");
        }
        short ethernetType = (short) Integer.parseInt(matcher.group(1), 16);
        return new EthType(ethernetType);
    }

    /**
     * Decodes the JSON into an instruction object.
     *
     * @return Criterion object
     * @throws IllegalArgumentException if the JSON is invalid
     */
    public Instruction decode() {
        String type = nullIsIllegal(json.get(InstructionCodec.TYPE),
                InstructionCodec.TYPE + InstructionCodec.ERROR_MESSAGE).asText();

        if (type.equals(Instruction.Type.OUTPUT.name())) {
            return Instructions.createOutput(getPortNumber(json));
        } else if (type.equals(Instruction.Type.NOACTION.name())) {
            return Instructions.createNoAction();
        } else if (type.equals(Instruction.Type.TABLE.name())) {
            return Instructions.transition(nullIsIllegal(json.get(InstructionCodec.TABLE_ID),
                    InstructionCodec.TABLE_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt());
        } else if (type.equals(Instruction.Type.GROUP.name())) {
            GroupId groupId = new GroupId(nullIsIllegal(json.get(InstructionCodec.GROUP_ID),
                    InstructionCodec.GROUP_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asInt());
            return Instructions.createGroup(groupId);
        } else if (type.equals(Instruction.Type.METER.name())) {
            MeterId meterId = MeterId.meterId(nullIsIllegal(json.get(InstructionCodec.METER_ID),
                    InstructionCodec.METER_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asLong());
            return Instructions.meterTraffic(meterId);
        } else if (type.equals(Instruction.Type.QUEUE.name())) {
            long queueId = nullIsIllegal(json.get(InstructionCodec.QUEUE_ID),
                    InstructionCodec.QUEUE_ID + InstructionCodec.MISSING_MEMBER_MESSAGE).asLong();
            if (json.get(InstructionCodec.PORT) == null ||
                    json.get(InstructionCodec.PORT).isNull()) {
                return Instructions.setQueue(queueId, null);
            } else {
                return Instructions.setQueue(queueId, getPortNumber(json));
            }
        } else if (type.equals(Instruction.Type.L0MODIFICATION.name())) {
            return decodeL0();
        } else if (type.equals(Instruction.Type.L1MODIFICATION.name())) {
            return decodeL1();
        } else if (type.equals(Instruction.Type.L2MODIFICATION.name())) {
            return decodeL2();
        } else if (type.equals(Instruction.Type.L3MODIFICATION.name())) {
            return decodeL3();
        } else if (type.equals(Instruction.Type.L4MODIFICATION.name())) {
            return decodeL4();
        } else if (type.equals(Instruction.Type.EXTENSION.name())) {
            return decodeExtension();
        } else if (type.equals(Instruction.Type.POFINSTRUCTION.name())) {
            return decodePof();
        }
        throw new IllegalArgumentException("Instruction type "
                + type + " is not supported");
    }
}
