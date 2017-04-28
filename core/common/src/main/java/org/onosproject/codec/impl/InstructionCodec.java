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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.onosproject.net.flow.instructions.Instruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Instruction codec.
 */
public final class InstructionCodec extends JsonCodec<Instruction> {

    protected static final Logger log = LoggerFactory.getLogger(InstructionCodec.class);

    protected static final String TYPE = "type";
    protected static final String SUBTYPE = "subtype";
    protected static final String PORT = "port";
    protected static final String MAC = "mac";
    protected static final String VLAN_ID = "vlanId";
    protected static final String VLAN_PCP = "vlanPcp";
    protected static final String MPLS_LABEL = "label";
    protected static final String MPLS_BOS = "bos";
    protected static final String IP = "ip";
    protected static final String FLOW_LABEL = "flowLabel";
    protected static final String LAMBDA = "lambda";
    protected static final String GRID_TYPE = "gridType";
    protected static final String CHANNEL_SPACING = "channelSpacing";
    protected static final String SPACING_MULTIPLIER = "spacingMultiplier";
    protected static final String SLOT_GRANULARITY = "slotGranularity";
    protected static final String ETHERNET_TYPE = "ethernetType";
    protected static final String TUNNEL_ID = "tunnelId";
    protected static final String TCP_PORT = "tcpPort";
    protected static final String UDP_PORT = "udpPort";
    protected static final String TABLE_ID = "tableId";
    protected static final String GROUP_ID = "groupId";
    protected static final String METER_ID = "meterId";
    protected static final String QUEUE_ID = "queueId";
    protected static final String TRIBUTARY_PORT_NUMBER = "tributaryPortNumber";
    protected static final String TRIBUTARY_SLOT_LEN = "tributarySlotLength";
    protected static final String TRIBUTARY_SLOT_BITMAP = "tributarySlotBitmap";
    protected static final String EXTENSION = "extension";
    protected static final String DEVICE_ID = "deviceId";
    protected static final String POF_INSTRUCTION = "pofInstruction";
    protected static final String ACTIONS = "actions";
    protected static final String POF_ACTION = "pofAction";
    protected static final String WRITE_LENGTH = "writeLength";
    protected static final String VALUE = "value";
    protected static final String MASK = "mask";
    protected static final String METADATA_OFFSET = "metadataOffset";
    protected static final String METADATA_LENGTH = "metadataLength";
    protected static final String PACKET_OFFSET = "packetOffset";
    protected static final String FIELD_ID = "fieldId";
    protected static final String FIELD_POSITION = "fieldPosition";
    protected static final String FIELD_LENGTH = "fieldLength";
    protected static final String FIELD_VALUE = "fieldValue";
    protected static final String CHECKSUM_POS_TYPE = "checksumPosType";
    protected static final String CALC_POS_TYPE = "calcPosType";
    protected static final String CHECKSUM_POSITION = "checksumPosition";
    protected static final String CHECKSUM_LENGTH = "checksumLength";
    protected static final String CALC_START_POSITION = "calcStartPosition";
    protected static final String CALC_LENGTH = "calcLength";
    protected static final String COUNTER_ID = "counterId";
    protected static final String TAG_POSITION = "tagPosition";
    protected static final String TAG_LENGTH_VALUE = "tagLengthValue";
    protected static final String REASON = "reason";
    protected static final String MATCH_FIELD = "matchField";
    protected static final String INCREMENT = "increment";
    protected static final String OF_CALC_TYPE = "ofCalcType";
    protected static final String SRC_VALUE_TYPE = "srcValueType";
    protected static final String SRC_VALUE = "srcValue";
    protected static final String DST_FIELD = "dstField";
    protected static final String SRC_FIELD = "srcField";
    protected static final String NEXT_TABLE_ID = "nextTableId";
    protected static final String MATCH_FIELD_NUM = "matchFieldNum";
    protected static final String MATCH_LIST = "matchList";
    protected static final String INDEX_TYPE = "indexType";
    protected static final String INDEX_VALUE = "indexValue";
    protected static final String INDEX_FIELD = "indexField";
    protected static final String MISSING_MEMBER_MESSAGE =
            " member is required in Instruction";
    protected static final String ERROR_MESSAGE =
            " not specified in Instruction";


    @Override
    public ObjectNode encode(Instruction instruction, CodecContext context) {
        checkNotNull(instruction, "Instruction cannot be null");

        return new EncodeInstructionCodecHelper(instruction, context).encode();
    }

    @Override
    public Instruction decode(ObjectNode json, CodecContext context) {
        if (json == null || !json.isObject()) {
            return null;
        }

        return new DecodeInstructionCodecHelper(json, context).decode();
    }
}
