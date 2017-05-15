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
package org.onosproject.net.flow.criteria;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Implementation of pof criterion.
 */
public final class PofCriterion implements Criterion {
    private final ArrayList<Criterion> list;
    private final String fieldName;
    private final short fieldId;
    private final short offset;
    private final short length;

    private final byte[] value;
    private final byte[] mask;
    private final Type type = Type.POF;

    /**
     * Constructor.
     * @param filedName the name of field to match
     * @param fieldId the id of field to match
     * @param offset the offset of field to match
     * @param length  the length of field to match
     * @param value the value of field to match
     * @param mask  the mask of field to match
     */
    PofCriterion(String filedName, short fieldId, short offset, short length, String value, String mask) {
        this.list = null;
        this.fieldName = filedName;
        this.fieldId = fieldId;
        this.offset = offset;
        this.length = length;
        this.value = hexStringToBytes(value);
        this.mask = hexStringToBytes(mask);
    }

    /**
     * Constructor.
     * @param fieldId the id of field to match
     * @param offset the offset of field to match
     * @param length  the length of field to match
     * @param value the value of field to match
     * @param mask  the mask of field to match
     */
    PofCriterion(short fieldId, short offset, short length, String value, String mask) {
        this.list = null;
        this.fieldName = null;
        this.fieldId = fieldId;
        this.offset = offset;
        this.length = length;
        this.value = hexStringToBytes(value);
        this.mask = hexStringToBytes(mask);
    }

    PofCriterion(ArrayList<Criterion> list) {
        this.list = list;
        this.fieldName = null;
        this.fieldId = -1;
        this.offset = -1;
        this.length = -1;
        this.value = hexStringToBytes("-1");
        this.mask = hexStringToBytes("-1");
    }
    @Override
    public Type type() {
        return this.type;
    }

    /**
     * Gets the MAC address to match.
     *
     * @return the MAC address to match
     */
    public String fieldName() {
        return this.fieldName;
    }

    /**
     * Gets the mask for the MAC address to match.
     *
     * @return the MAC address to match
     */
    public short fieldId() {
        return this.fieldId;
    }

    /**
     *
     * @return
     */
    public short offset() {
        return this.offset;
    }

    /**
     *
     * @return
     */
    public short length() {
        return this.length;
    }

    /**
     *
     *@return
     */
    public byte[] value() {
        return this.value;
    }

    /**
     *
     * @return
     */
    public byte[] mask() {
        return this.mask;
    }

    /**
     *
     * @return
     */
    public ArrayList<Criterion> list() {
        return this.list;
    }



    @Override
    public String toString() {
        /*
        return (mask != null) ?
            type().toString() + SEPARATOR + mac + "/" + mask :
            type().toString() + SEPARATOR + mac;
            */
        /*
        return ";fid=" + fieldId +
                ";ofst=" + offset +
                ";len=" + length +
                ";val=" + HexString.toHex(value) +
                ";mask=" + HexString.toHex(mask);
                */
        String str = "";
        for (int i = 0; i < list.size(); i++) {
            PofCriterion criterion = (PofCriterion) list.get(i);
            str += criterion.type().toString() + SEPARATOR + criterion.fieldId()
                    + SEPARATOR + criterion.offset() + "/" + criterion.length()
                    + SEPARATOR + criterion.value().toString() + "/" + criterion.mask().toString()
                    + ";";
        }
        return str;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type.ordinal(), fieldId, offset, length, value, mask);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof PofCriterion) {
            PofCriterion that = (PofCriterion) obj;
            return Objects.equals(fieldId, that.fieldId) &&
                    Objects.equals(offset, that.offset) &&
                    Objects.equals(length, that.length) &&
                    Objects.equals(value, that.value) &&
                    Objects.equals(mask, that.mask) &&
                    Arrays.equals(value, that.value) &&
                    Arrays.equals(mask, that.mask) &&
                    Objects.equals(list, that.list) &&
                    Objects.equals(type, that.type);
        }
        return false;
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
