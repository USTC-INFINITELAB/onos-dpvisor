package org.onosproject.codec.impl;

import org.onosproject.floodlightpof.protocol.table.OFTableType;

/**
 * FlowTable type JSON codec.
 */
public class FlowTableTypeCodec {
    String type;

    public  FlowTableTypeCodec(String type) {
        this.type = type;
    }

    public OFTableType getFlowTbleType() {
        OFTableType tableType = OFTableType.OF_MM_TABLE;
        if (this.type.equals("OF_MM_TABLE")) {
            tableType = OFTableType.OF_MM_TABLE;
        } else if (this.type.equals("OF_LPM_TABLE")) {
            tableType = OFTableType.OF_LPM_TABLE;
        } else if (this.type.equals("OF_EM_TABLE")) {
            tableType = OFTableType.OF_EM_TABLE;
        } else if (this.type.equals("OF_LINEAR_TABLE")) {
            tableType = OFTableType.OF_LINEAR_TABLE;
        } else if (this.type.equals("OF_MAX_TABLE_TYPE")) {
            tableType = OFTableType.OF_MAX_TABLE_TYPE;
        }
        return tableType;
    }
}
