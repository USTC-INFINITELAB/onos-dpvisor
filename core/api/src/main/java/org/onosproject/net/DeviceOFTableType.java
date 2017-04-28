package org.onosproject.net;

import org.onosproject.floodlightpof.protocol.table.OFTableType;

/**
 * Created by niubin on 17-4-23.
 */
public class DeviceOFTableType {

    public DeviceId deviceId;
    public OFTableType ofTableType;
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
