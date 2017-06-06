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
package org.onosproject.net.flow.instructions;

import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.instruction.OFInstruction;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionApplyActions;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionCalculateField;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionGotoDirectTable;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionGotoTable;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionWriteMetadata;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionWriteMetadataFromPacket;

import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Factory class for creating various traffic treatment instructions.
 */
public final class DefaultPofInstructions {

    private static final String SEPARATOR = ":";

    // Ban construction
    private DefaultPofInstructions() {}

    /**
     *
     */
    public static PofInstruction applyActions(List<OFAction> actions) {
        checkNotNull(actions, "actions cannot be null");
        return new PofInstructionApplyActions(actions);
    }


    /**
     *
     */
    public static PofInstruction calculateField(OFInstructionCalculateField.OFCalcType type,
                                                byte srcValueType, OFMatch20 destField,
                                                int srcValue, OFMatch20 srcField) {
        checkNotNull(type, "type cannot be null");
        checkNotNull(srcValueType, "srcValueType cannot be null");
        checkNotNull(destField, "destField cannot be null");
        checkNotNull(srcValue, "srcValue cannot be null");
        checkNotNull(srcField, "srcField cannot be null");

        return new PofInstructionCalcField(type, srcValueType, destField, srcValue, srcField);
    }

    public static PofInstruction calculateField(OFInstructionCalculateField calcField) {
        checkNotNull(calcField, "calcField cannot be null");

        return new PofInstructionCalcField(calcField);
    }

    /**
     *
     */
    public static PofInstruction gotoDirectTable(byte nextTableId, byte indexType,
                                                 short packetOffset, int indexValue,
                                                 OFMatch20 indexFiled) {
        checkNotNull(nextTableId, "nextTableId cannot be null");
        checkNotNull(indexType, "indexType cannot be null");
        checkNotNull(packetOffset, "packetOffset cannot be null");
        checkNotNull(indexValue, "indexValue cannot be null");
        checkNotNull(indexFiled, "indexFiled cannot be null");

        return new PofInstructionGotoDirectTable(nextTableId, indexType, packetOffset, indexValue, indexFiled);
    }
    public static PofInstruction gotoDirectTable(OFInstructionGotoDirectTable gotoDirectTable) {
        checkNotNull(gotoDirectTable, "gotoDirectTable cannot be null");

        return new PofInstructionGotoDirectTable(gotoDirectTable);
    }

    /**
     *
     */
    public static PofInstruction gotoTable(byte nextTableId, byte matchFieldNum,
                                           short packetOffset, List<OFMatch20> matchList) {
        checkNotNull(nextTableId, "nextTableId cannot be null");
        checkNotNull(matchFieldNum, "matchFieldNum cannot be null");
        checkNotNull(packetOffset, "packetOffset cannot be null");
        checkNotNull(matchList, "matchList cannot be null");

        return new PofInstructionGotoTable(nextTableId, matchFieldNum, packetOffset, matchList);
    }

    public static PofInstruction gotoTable(OFInstructionGotoTable gotoTable) {
        checkNotNull(gotoTable, "gotoTable cannot be null");

        return new PofInstructionGotoTable(gotoTable);
    }


    /**
     *
     */
    public static PofInstruction writeMetadata(short metadataOffset, short writeLength, byte[] value) {
        checkNotNull(metadataOffset, "metadataOffset cannot be null");
        checkNotNull(writeLength, "writeLength cannot be null");
        checkNotNull(value, "value cannot be null");

        return new PofInstructionWriteMetadata(metadataOffset, writeLength, value);
    }

    public static PofInstruction writeMetadata(OFInstructionWriteMetadata writeMetadata) {
        checkNotNull(writeMetadata, "writeMetadata cannot be null");

        return new PofInstructionWriteMetadata(writeMetadata);
    }

    /**
     *
     */
    public static PofInstruction writeMetadataFromPacket(short metadataOffset, short packetOffset, short writeLength) {
        checkNotNull(metadataOffset, "metadataOffset cannot be null");
        checkNotNull(writeLength, "writeLength cannot be null");
        checkNotNull(packetOffset, "packetOffset cannot be null");

        return new PofInstructionWriteMetadataFromPacket(metadataOffset, packetOffset, writeLength);
    }

    public static PofInstruction writeMetadataFromPacket(OFInstructionWriteMetadataFromPacket writeMetadataFromPacket) {
        checkNotNull(writeMetadataFromPacket, "writeMetadataFromPacket cannot be null");

        return new PofInstructionWriteMetadataFromPacket(writeMetadataFromPacket);
    }

    /**
     *  apply actions Instruction.
     */
    public static final class PofInstructionApplyActions implements PofInstruction {

        private final OFInstructionApplyActions insApplyActions;

        private PofInstructionApplyActions(List<OFAction> actions) {
            this.insApplyActions = new OFInstructionApplyActions();
            this.insApplyActions.setActionNum((byte) actions.size());
            this.insApplyActions.setActionList(actions);
        }

        @Override
        public PofInstructionType pofInstructionType() {
            return PofInstructionType.POF_ACTION;
        }

        @Override
        public OFInstruction instruction() {
            return this.insApplyActions;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFINSTRUCTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + insApplyActions.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), insApplyActions);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFInstructionApplyActions) {
                OFInstructionApplyActions that = (OFInstructionApplyActions) obj;
                return Objects.equals(insApplyActions.getActionList(), that.getActionList());

            }
            return false;
        }
    }

    /**
     *  calc field Instruction.
     */
    public static final class PofInstructionCalcField implements PofInstruction {
        private final OFInstructionCalculateField insCalculateField;

        private PofInstructionCalcField(OFInstructionCalculateField.OFCalcType type,
                                        byte srcValueType, OFMatch20 destField,
                                        int srcValue, OFMatch20 srcField) {
            insCalculateField = new OFInstructionCalculateField();
            insCalculateField.setCalcType(type);
            insCalculateField.setSrcValueType(srcValueType);
            insCalculateField.setDesField(destField);
            insCalculateField.setSrcValue(srcValue);
            insCalculateField.setSrcField(srcField);
        }

        private PofInstructionCalcField(OFInstructionCalculateField calcField) {
            this.insCalculateField = calcField;
        }

        @Override
        public PofInstructionType pofInstructionType() {
            return PofInstructionType.CALCULATE_FIELD;
        }

        @Override
        public OFInstruction instruction() {
            return this.insCalculateField;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFINSTRUCTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + insCalculateField.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), insCalculateField);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFInstructionCalculateField) {
                OFInstructionCalculateField that = (OFInstructionCalculateField) obj;
                return Objects.equals(insCalculateField.getSrcField(), that.getSrcField()) &&
                        Objects.equals(insCalculateField.getDesField(), that.getDesField());

            }
            return false;
        }
    }

    /**
     *  PofInstructionGotoDirectTable Instruction.
     */
    public static final class PofInstructionGotoDirectTable implements PofInstruction {
        private final OFInstructionGotoDirectTable insGotoDirectTable;

        private PofInstructionGotoDirectTable(byte nextTableId, byte indexType,
                                              short packetOffset, int indexValue,
                                              OFMatch20 indexFiled) {
            insGotoDirectTable = new OFInstructionGotoDirectTable();
            insGotoDirectTable.setNextTableId(nextTableId);
            insGotoDirectTable.setIndexType(indexType);
            insGotoDirectTable.setPacketOffset(packetOffset);
            insGotoDirectTable.setIndexValue(indexValue);
            insGotoDirectTable.setIndexField(indexFiled);
        }

        private PofInstructionGotoDirectTable(OFInstructionGotoDirectTable gotoDirectTable) {
            this.insGotoDirectTable = gotoDirectTable;
        }

        @Override
        public PofInstructionType pofInstructionType() {
            return PofInstructionType.GOTO_DIRECT_TABLE;
        }

        @Override
        public OFInstruction instruction() {
            return this.insGotoDirectTable;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFINSTRUCTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + insGotoDirectTable.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), insGotoDirectTable);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFInstructionGotoDirectTable) {
                OFInstructionGotoDirectTable that = (OFInstructionGotoDirectTable) obj;
                return Objects.equals(insGotoDirectTable.getNextTableId(), that.getNextTableId()) &&
                        Objects.equals(insGotoDirectTable.getIndexValue(), that.getIndexValue());

            }
            return false;
        }
    }


    /**
     *  PofInstructionGotoDTable Instruction.
     */
    public static final class PofInstructionGotoTable implements PofInstruction {
        private final OFInstructionGotoTable insGotoTable;

        private PofInstructionGotoTable(byte nextTableId, byte matchFieldNum,
                                        short packetOffset, List<OFMatch20> matchList) {
            insGotoTable = new OFInstructionGotoTable();
            insGotoTable.setNextTableId(nextTableId);
            insGotoTable.setMatchFieldNum(matchFieldNum);
            insGotoTable.setPacketOffset(packetOffset);
            insGotoTable.setMatchList(matchList);
        }

        private PofInstructionGotoTable(OFInstructionGotoTable gotoTable) {
            this.insGotoTable = gotoTable;
        }

        @Override
        public PofInstructionType pofInstructionType() {
            return PofInstructionType.GOTO_TABLE;
        }

        @Override
        public OFInstruction instruction() {
            return this.insGotoTable;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFINSTRUCTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + insGotoTable.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), insGotoTable);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFInstructionGotoTable) {
                OFInstructionGotoTable that = (OFInstructionGotoTable) obj;
                return Objects.equals(insGotoTable.getNextTableId(), that.getNextTableId()) &&
                        Objects.equals(insGotoTable.getPacketOffset(), that.getPacketOffset()) &&
                        Objects.equals(insGotoTable.getMatchList(), that.getMatchList());

            }
            return false;
        }
    }

    /**
     *  PofInstructionWriteMetadata Instruction.
     */
    public static final class PofInstructionWriteMetadata implements PofInstruction {
        private final OFInstructionWriteMetadata insWriteMetadata;

        private PofInstructionWriteMetadata(short metadataOffset, short writeLength, byte[] value) {
            insWriteMetadata = new OFInstructionWriteMetadata();
            insWriteMetadata.setMetadataOffset(metadataOffset);
            insWriteMetadata.setWriteLength(writeLength);
            insWriteMetadata.setValue(value);

        }

        private PofInstructionWriteMetadata(OFInstructionWriteMetadata writeMetadata) {
            this.insWriteMetadata = writeMetadata;
        }

        @Override
        public PofInstructionType pofInstructionType() {
            return PofInstructionType.WRITE_METADATA;
        }

        @Override
        public OFInstruction instruction() {
            return this.insWriteMetadata;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFINSTRUCTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + insWriteMetadata.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), insWriteMetadata);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFInstructionWriteMetadata) {
                OFInstructionWriteMetadata that = (OFInstructionWriteMetadata) obj;
                return Objects.equals(insWriteMetadata.getMetadataOffset(), that.getMetadataOffset()) &&
                        Objects.equals(insWriteMetadata.getWriteLength(), that.getWriteLength()) &&
                        Objects.equals(insWriteMetadata.getValue(), that.getValue());

            }
            return false;
        }
    }

    /**
     *  PofInstructionWriteMetadataFromPacket Instruction.
     */
    public static final class PofInstructionWriteMetadataFromPacket implements PofInstruction {
        private final OFInstructionWriteMetadataFromPacket insWriteMetadataFromPacket;

        private PofInstructionWriteMetadataFromPacket(short metadataOffset, short packetOffset, short writeLength) {
            insWriteMetadataFromPacket = new OFInstructionWriteMetadataFromPacket();
            insWriteMetadataFromPacket.setMetadataOffset(metadataOffset);
            insWriteMetadataFromPacket.setPacketOffset(packetOffset);
            insWriteMetadataFromPacket.setWriteLength(writeLength);

        }

        private PofInstructionWriteMetadataFromPacket(OFInstructionWriteMetadataFromPacket writeMetadataFromPacket) {
            this.insWriteMetadataFromPacket = writeMetadataFromPacket;
        }

        @Override
        public PofInstructionType pofInstructionType() {
            return PofInstructionType.WRITE_METADATA_FROM_PACKET;
        }

        @Override
        public OFInstruction instruction() {
            return this.insWriteMetadataFromPacket;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFINSTRUCTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + insWriteMetadataFromPacket.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), insWriteMetadataFromPacket);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFInstructionWriteMetadataFromPacket) {
                OFInstructionWriteMetadataFromPacket that = (OFInstructionWriteMetadataFromPacket) obj;
                return Objects.equals(insWriteMetadataFromPacket.getMetadataOffset(), that.getMetadataOffset()) &&
                        Objects.equals(insWriteMetadataFromPacket.getWriteLength(), that.getWriteLength()) &&
                        Objects.equals(insWriteMetadataFromPacket.getPacketOffset(), that.getPacketOffset());

            }
            return false;
        }
    }

}

