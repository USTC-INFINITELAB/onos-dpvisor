package org.onosproject.net.table;

import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.DeviceId;

/**
 * OFTableType of the given deviceId.
 */
public class DeviceOFTableType {

    private DeviceId deviceId;
    private OFTableType ofTableType;
    public DeviceOFTableType(DeviceId deviceId, OFTableType ofTableType) {
        this.deviceId = deviceId;
        this.ofTableType = ofTableType;
    }
    public DeviceId getDeviceId() {
        return this.deviceId;
    }
    public OFTableType getOfTableType() {
        return this.ofTableType;
    }


}
