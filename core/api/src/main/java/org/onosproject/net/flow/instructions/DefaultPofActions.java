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
import org.onosproject.floodlightpof.protocol.OFMatchX;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.action.OFActionAddField;
import org.onosproject.floodlightpof.protocol.action.OFActionCalculateCheckSum;
import org.onosproject.floodlightpof.protocol.action.OFActionCounter;
import org.onosproject.floodlightpof.protocol.action.OFActionDeleteField;
import org.onosproject.floodlightpof.protocol.action.OFActionDrop;
import org.onosproject.floodlightpof.protocol.action.OFActionGroup;
import org.onosproject.floodlightpof.protocol.action.OFActionModifyField;
import org.onosproject.floodlightpof.protocol.action.OFActionOutput;
import org.onosproject.floodlightpof.protocol.action.OFActionPacketIn;
import org.onosproject.floodlightpof.protocol.action.OFActionSetField;
import org.onosproject.floodlightpof.protocol.action.OFActionSetFieldFromMetadata;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Factory class for creating various traffic treatment instructions.
 */
public final class DefaultPofActions {

    private static final String SEPARATOR = ":";

    public enum PofActionType {

        OUTPUT,

        SET_FIELD,

        SET_FIELD_FROM_METADATA,

        MODIFY_FIELD,

        ADD_FIELD,

        DELETE_FIELD,

        CALCULATE_CHECKSUM,

        GROUP,

        DROP,

        PACKET_IN,

        COUNTER

        //TODO: remaining types
    }

    // Ban construction
    private DefaultPofActions() {}
    public static PofAction addField(short fieldId, short fieldPosition, int fieldLength, String fieldValue) {
        checkNotNull(fieldId, "fieldId cannot be null");
        checkNotNull(fieldPosition, "fieldPosition cannot be null");
        checkNotNull(fieldLength, "fieldLength cannot be null");
        checkNotNull(fieldValue, "fieldValue cannot be null");

        return new PofActionAddField(fieldId, fieldPosition, fieldLength, hexStringToBytes(fieldValue));
    }

    public static PofAction addField(OFActionAddField action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionAddField(action);
    }

    /**
     *
     */
    public static PofAction calcCheckSum(byte checksumPosType, byte calcPosType,
                                         short checksumPosition, short checksumLength,
                                         short calcStartPosition, short calcLength)  {
        checkNotNull(checksumPosType, "checksumPosType cannot be null");
        checkNotNull(calcPosType, "calcPosType cannot be null");
        checkNotNull(checksumPosition, "checksumPosition cannot be null");
        checkNotNull(checksumLength, "checksumLength cannot be null");
        checkNotNull(calcStartPosition, "calcStartPosition cannot be null");
        checkNotNull(calcLength, "calcLength cannot be null");
        return new PofActionCalcCheckSum(checksumPosType, calcPosType,
                checksumPosition, checksumLength,
                calcStartPosition, calcLength);
    }

    public static PofAction pofActionCalcCheckSum(OFActionCalculateCheckSum action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionCalcCheckSum(action);
    }

    /**
     *
     */
    public static PofAction counter(int counterId) {
        checkNotNull(counterId, "counterId cannot be null");

        return new PofActionCounter(counterId);
    }

    public static PofAction pofActionCounter(OFActionCounter action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionCounter(action);
    }


    /**
     *
     */

    public static PofAction deleteField(int tagPosition, int tagLengthValue) {

        checkNotNull(tagLengthValue, "tagLengthField cannot be null");

        return new PofActionDeleteField((short) tagPosition, tagLengthValue);
    }


    public static PofAction deleteField(int tagPosition, OFMatch20 tagLengthField) {

        checkNotNull(tagLengthField, "tagLengthField cannot be null");

        return new PofActionDeleteField((short) tagPosition, tagLengthField);
    }

    public static PofAction deleteField(OFActionDeleteField action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionDeleteField(action);
    }

    /**
     *
     */
    public static PofAction drop(int reason) {
        checkNotNull(reason, "reason cannot be null");

        return new PofActionDrop(reason);
    }

    public static PofAction drop(OFActionDrop action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionDrop(action);
    }


    /**
     *
     */
    public static PofAction group(int groupId) {
        checkNotNull(groupId, "groupId cannot be null");

        return new PofActionGroup(groupId);
    }

    public static PofAction group(OFActionGroup action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionGroup(action);
    }


    /**
     *
     */
    public static PofAction modifyField(OFMatch20 matchField, int increment) {
        checkNotNull(matchField, "matchField cannot be null");
        checkNotNull(increment, "increment cannot be null");

        return new PofActionModifyField(matchField, increment);
    }

    public static PofAction modifyField(OFActionModifyField action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionModifyField(action);
    }

    /**
     *
     */
    public static PofAction output(short metadateOffset,
                                   short metadataLength, short packetOffset, int protId) {
        checkNotNull(protId, "protId cannot be null");
        checkNotNull(metadateOffset, "metadateOffset cannot be null");
        checkNotNull(metadataLength, "metadataLength cannot be null");
        checkNotNull(packetOffset, "packetOffset cannot be null");
        return new PofActionOutput((byte) 0, metadateOffset, metadataLength, packetOffset, protId);
    }

    public static PofAction output(short metadateOffset,
                                   short metadataLength, short packetOffset, OFMatch20 protIdField) {
        checkNotNull(protIdField, "protIdField cannot be null");
        checkNotNull(metadateOffset, "metadateOffset cannot be null");
        checkNotNull(metadataLength, "metadataLength cannot be null");
        checkNotNull(packetOffset, "packetOffset cannot be null");

        return new PofActionOutput((byte) 1, metadateOffset, metadataLength, packetOffset, protIdField);
    }

    public static PofAction output(OFActionOutput action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionOutput(action);
    }

    /**
     *
     */
    public static PofAction packetIn(int reason) {
        checkNotNull(reason, "reason cannot be null");

        return new PofActionPacketIn(reason);
    }

    public static PofAction packetIn(OFActionPacketIn action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionPacketIn(action);
    }


    /**
     *
     */

    public static PofAction setField(short fieldId, short fieldOffset, int fieldLen, String value, String mask) {

        checkNotNull(fieldId, "fieldSetting cannot be null");
        checkNotNull(fieldLen, "fieldLen cannot be null");
        checkNotNull(fieldOffset, "fieldOffset cannot be null");
        checkNotNull(value, "value cannot be null");
        checkNotNull(mask, "value cannot be null");
        checkArgument(value.length() == fieldLen / 4, "The length of Value must equals to the" +
                "length of field");
        checkArgument(mask.length() == fieldLen / 4, "The length of mask must equals to the" +
                "length of field");

        OFMatchX ofMatchX = new OFMatchX();
        ofMatchX.setFieldId(fieldId);
        ofMatchX.setLength((short) fieldLen);
        ofMatchX.setOffset(fieldOffset);
        ofMatchX.setValue(hexStringToBytes((value)));
        ofMatchX.setMask(hexStringToBytes(mask));
        return new PofActionSetField(ofMatchX);
    }


    public static PofAction setField(OFMatch20 field, String value, String mask) {

        checkNotNull(field, "fieldSetting cannot be null");
        checkNotNull(value, "value cannot be null");
        checkNotNull(mask, "value cannot be null");
        checkArgument(value.length() == field.getLength() / 4, "The length of Value must equals to the " +
                "length of field");
        checkArgument(mask.length() == field.getLength() / 4, "The length of mask must equals to the " +
                "length of field");
        OFMatchX ofMatchX = new OFMatchX();
        ofMatchX.setFieldId(field.getFieldId());
        ofMatchX.setLength(field.getLength());
        ofMatchX.setOffset(field.getOffset());
        ofMatchX.setFieldName(field.getFieldName());
        ofMatchX.setValue(hexStringToBytes(value));
        ofMatchX.setMask(hexStringToBytes(mask));
        return new PofActionSetField(ofMatchX);
    }

    public static PofAction setField(OFMatchX fieldSetting) {
        checkNotNull(fieldSetting, "fieldSetting cannot be null");

        return new PofActionSetField(fieldSetting);
    }

    public static PofAction setField(OFActionSetField action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionSetField(action);
    }


    /**
     *
     */
    public static PofAction setFieldFromMetadata(OFMatch20 fieldSetting, short metadataOffset) {
        checkNotNull(fieldSetting, "fieldSetting cannot be null");
        checkNotNull(metadataOffset, "metadataOffset cannot be null");

        return new PofActionSetFieldFromMetadata(fieldSetting, metadataOffset);
    }

    public static PofAction setFieldFromMetadata(OFActionSetFieldFromMetadata action) {
        checkNotNull(action, "action cannot be null");

        return new PofActionSetFieldFromMetadata(action);
    }
    /**
     *  Pof apply action add field Instruction.
     */
    public static final class PofActionAddField implements PofAction {
        private final OFActionAddField actionAddField;

        private PofActionAddField(short fieldId, short fieldPosition, int fieldLength, byte[] fieldValue) {
            actionAddField = new OFActionAddField();
            actionAddField.setFieldId(fieldId);
            actionAddField.setFieldPosition(fieldPosition);
            actionAddField.setFieldLength(fieldLength);
            actionAddField.setFieldValue(fieldValue);

        }

        private PofActionAddField(OFActionAddField actionAddField) {
            this.actionAddField = actionAddField;
        }

        @Override
        public OFAction action() {
            return this.actionAddField;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionAddField.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionAddField);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionAddField) {
                OFActionAddField that = (OFActionAddField) obj;
                return Objects.equals(actionAddField.getFieldPosition(), that.getFieldPosition()) &&
                        Objects.equals(actionAddField.getFieldLength(), that.getLength()) &&
                        Objects.equals(actionAddField.getFieldValue(), that.getFieldValue());

            }
            return false;
        }
    }

    /**
     *  Pof apply action calc check sum Instruction.
     */
    public static final class PofActionCalcCheckSum implements PofAction {
        private final OFActionCalculateCheckSum actionCalculateCheckSum;

        private PofActionCalcCheckSum(byte checksumPosType, byte calcPosType,
                                      short checksumPosition, short checksumLength,
                                      short calcStartPosition, short calcLength) {
            actionCalculateCheckSum = new OFActionCalculateCheckSum();
            actionCalculateCheckSum.setChecksumPosType(checksumPosType);
            actionCalculateCheckSum.setCalcPosType(calcPosType);
            actionCalculateCheckSum.setChecksumPosition(checksumPosition);
            actionCalculateCheckSum.setChecksumLength(checksumLength);
            actionCalculateCheckSum.setCalcStartPosition(calcStartPosition);
            actionCalculateCheckSum.setCalcLength(calcLength);

        }

        private PofActionCalcCheckSum(OFActionCalculateCheckSum actionCalculateCheckSum) {
            this.actionCalculateCheckSum = actionCalculateCheckSum;
        }

        @Override
        public OFAction action() {
            return this.actionCalculateCheckSum;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionCalculateCheckSum.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionCalculateCheckSum);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionCalculateCheckSum) {
                OFActionCalculateCheckSum that = (OFActionCalculateCheckSum) obj;
                return  Objects.equals(actionCalculateCheckSum.getChecksumPosition(), that.getChecksumPosition()) &&
                        Objects.equals(actionCalculateCheckSum.getChecksumLength(), that.getChecksumLength()) &&
                        Objects.equals(actionCalculateCheckSum.getCalcLength(), that.getCalcLength()) &&
                        Objects.equals(actionCalculateCheckSum.getCalcStartPosition(), that.getCalcStartPosition());

            }
            return false;
        }
    }

    /**
     *  Pof apply action counter Instruction.
     */
    public static final class PofActionCounter implements PofAction {
        private final OFActionCounter actionCounter;

        private PofActionCounter(int counterId) {

            actionCounter = new OFActionCounter();
            actionCounter.setCounterId(counterId);
        }

        private PofActionCounter(OFActionCounter actionCounter) {
            this.actionCounter = actionCounter;
        }

        @Override
        public OFAction action() {
            return this.actionCounter;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionCounter.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionCounter);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionCounter) {
                OFActionCounter that = (OFActionCounter) obj;
                return Objects.equals(actionCounter.getCounterId(), that.getCounterId());
            }
            return false;
        }
    }

    /**
     *  Pof apply action delete field Instruction.
     */
    public static final class PofActionDeleteField implements PofAction {
        private final OFActionDeleteField actionDeleteField;

        private PofActionDeleteField(short tagPosition, int tagLengthValue) {

            actionDeleteField = new OFActionDeleteField();
            actionDeleteField.setTagPosition(tagPosition);
            actionDeleteField.setTagLengthValueType((byte) 0);
            actionDeleteField.setTagLengthValue(tagLengthValue);
        }

        private PofActionDeleteField(short tagPosition, OFMatch20 ofMatch20) {

            actionDeleteField = new OFActionDeleteField();
            actionDeleteField.setTagPosition(tagPosition);
            actionDeleteField.setTagLengthValueType((byte) 1);
            actionDeleteField.setTagLengthField(ofMatch20);
        }

        private PofActionDeleteField(OFActionDeleteField actionDeleteField) {
            this.actionDeleteField = actionDeleteField;
        }

        @Override
        public OFAction action() {
            return this.actionDeleteField;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionDeleteField.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionDeleteField);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionDeleteField) {
                OFActionDeleteField that = (OFActionDeleteField) obj;
                return Objects.equals(actionDeleteField.getTagPosition(), that.getTagPosition()) &&
                        Objects.equals(actionDeleteField.getTagLengthValueType(), that.getTagLengthValueType()) &&
                        Objects.equals(actionDeleteField.getTagLengthValue(), that.getTagLengthValue()) &&
                        Objects.equals(actionDeleteField.getTagLengthField(), that.getTagLengthField());

            }
            return false;
        }
    }

    /**
     *  Pof apply action drop Instruction.
     */
    public static final class PofActionDrop implements PofAction {
        private final OFActionDrop actionDrop;

        private PofActionDrop(int reason) {

            actionDrop = new OFActionDrop();
            actionDrop.setReason(reason);

        }

        private PofActionDrop(OFActionDrop actionDrop) {
            this.actionDrop = actionDrop;
        }

        @Override
        public OFAction action() {
            return this.actionDrop;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionDrop.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionDrop);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionDrop) {
                OFActionDrop that = (OFActionDrop) obj;
                return Objects.equals(actionDrop.getReason(), that.getReason());

            }
            return false;
        }
    }

    /**
     *  Pof apply action group Instruction.
     */
    public static final class PofActionGroup implements PofAction {
        private final OFActionGroup actionGroup;

        private PofActionGroup(int groupId) {

            actionGroup = new OFActionGroup();
            actionGroup.setGroupId(groupId);

        }

        private PofActionGroup(OFActionGroup actionGroup) {
            this.actionGroup = actionGroup;
        }

        @Override
        public OFAction action() {
            return this.actionGroup;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionGroup.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionGroup);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionGroup) {
                OFActionGroup that = (OFActionGroup) obj;
                return Objects.equals(actionGroup.getGroupId(), that.getGroupId());

            }
            return false;
        }
    }

    /**
     *  Pof apply action modify field Instruction.
     */
    public static final class PofActionModifyField implements PofAction {
        private final OFActionModifyField actionModifyField;

        private PofActionModifyField(OFMatch20 matchField, int increment) {

            actionModifyField = new OFActionModifyField();
            actionModifyField.setMatchField(matchField);
            actionModifyField.setIncrement(increment);

        }

        private PofActionModifyField(OFActionModifyField actionModifyField) {
            this.actionModifyField = actionModifyField;
        }

        @Override
        public OFAction action() {
            return this.actionModifyField;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionModifyField.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionModifyField);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionModifyField) {
                OFActionModifyField that = (OFActionModifyField) obj;
                return Objects.equals(actionModifyField.getMatchField(), that.getMatchField()) &&
                        Objects.equals(actionModifyField.getIncrement(), that.getIncrement());

            }
            return false;
        }
    }

    /**
     *  Pof apply action out put Instruction.
     */
    public static final class PofActionOutput implements PofAction {
        private final OFActionOutput actionOutput;

        private PofActionOutput(byte portIdValueType, short metadateOffset,
                                short metadataLength, short packetOffset, int portId) {
            if (portIdValueType == 0) {
                checkNotNull(portId);
            }
            actionOutput = new OFActionOutput();
            actionOutput.setPordIdValueType(portIdValueType);
            actionOutput.setPortId(portId);
            actionOutput.setMetadataOffset(metadateOffset);
            actionOutput.setMetadataLength(metadataLength);
            actionOutput.setPacketOffset(packetOffset);
        }

        private PofActionOutput(byte portIdValueType, short metadateOffset,
                                short metadataLength, short packetOffset,
                                OFMatch20 portIdField) {

            if (portIdValueType == 1) {
                checkNotNull(portIdField);
            }
            actionOutput = new OFActionOutput();
            actionOutput.setPordIdValueType(portIdValueType);
            actionOutput.setPortIdField(portIdField);
            actionOutput.setMetadataOffset(metadateOffset);
            actionOutput.setMetadataLength(metadataLength);
            actionOutput.setPacketOffset(packetOffset);
        }

        private PofActionOutput(OFActionOutput actionOutput) {
            this.actionOutput = actionOutput;
        }

        @Override
        public OFAction action() {
            return this.actionOutput;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionOutput.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionOutput);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionOutput) {
                OFActionOutput that = (OFActionOutput) obj;
                return Objects.equals(actionOutput.getPortId(), that.getPortId()) &&
                        Objects.equals(actionOutput.getMetadataOffset(), that.getMetadataOffset()) &&
                        Objects.equals(actionOutput.getMetadataLength(), that.getMetadataLength()) &&
                        Objects.equals(actionOutput.getPacketOffset(), that.getPacketOffset());

            }
            return false;
        }
    }

    /**
     *  Pof apply action packet in Instruction.
     */
    public static final class PofActionPacketIn implements PofAction {
        private final OFActionPacketIn actionPacketIn;

        private PofActionPacketIn(int reason) {
            actionPacketIn = new OFActionPacketIn();
            actionPacketIn.setReason(reason);

        }

        private PofActionPacketIn(OFActionPacketIn actionPacketIn) {
            this.actionPacketIn = actionPacketIn;
        }

        @Override
        public OFAction action() {
            return this.actionPacketIn;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionPacketIn.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionPacketIn);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionPacketIn) {
                OFActionPacketIn that = (OFActionPacketIn) obj;
                return Objects.equals(actionPacketIn.getReason(), that.getReason());

            }
            return false;
        }
    }

    /**
     *  Pof apply action set field Instruction.
     */
    public static final class PofActionSetField implements PofAction {
        private final OFActionSetField actionSetField;

        private PofActionSetField(OFMatchX fieldSetting) {
            actionSetField = new OFActionSetField();
            actionSetField.setFieldSetting(fieldSetting);


        }

        private PofActionSetField(OFActionSetField actionSetField) {
            this.actionSetField = actionSetField;
        }

        @Override
        public OFAction action() {
            return this.actionSetField;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionSetField.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionSetField);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionSetField) {
                OFActionSetField that = (OFActionSetField) obj;
                return Objects.equals(actionSetField.getFieldSetting(), that.getFieldSetting());

            }
            return false;
        }
    }

    /**
     *  Pof apply action set field from metadata Instruction.
     */
    public static final class PofActionSetFieldFromMetadata implements PofAction {
        private final OFActionSetFieldFromMetadata actionSetFieldFromMetadata;

        private PofActionSetFieldFromMetadata(OFMatch20 fieldSetting, short metadataOffset) {

            actionSetFieldFromMetadata = new OFActionSetFieldFromMetadata();
            actionSetFieldFromMetadata.setFieldSetting(fieldSetting);
            actionSetFieldFromMetadata.setMetadataOffset(metadataOffset);


        }

        private PofActionSetFieldFromMetadata(OFActionSetFieldFromMetadata actionSetFieldFromMetadata) {
            this.actionSetFieldFromMetadata = actionSetFieldFromMetadata;
        }

        @Override
        public OFAction action() {
            return this.actionSetFieldFromMetadata;
        }

        @Override
        public Instruction.Type type() {
            return Instruction.Type.POFACTION;
        }

        @Override
        public String toString() {
            return type().toString() + SEPARATOR + actionSetFieldFromMetadata.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type().ordinal(), actionSetFieldFromMetadata);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof OFActionSetFieldFromMetadata) {
                OFActionSetFieldFromMetadata that = (OFActionSetFieldFromMetadata) obj;
                return Objects.equals(actionSetFieldFromMetadata.getFieldSetting(), that.getFieldSetting()) &&
                        Objects.equals(actionSetFieldFromMetadata.getMetadataOffset(), that.getMetadataOffset());

            }
            return false;
        }
    }

    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }
    public static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

}


