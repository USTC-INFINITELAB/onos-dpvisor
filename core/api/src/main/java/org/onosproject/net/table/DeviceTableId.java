package org.onosproject.net.table;

import org.onosproject.net.DeviceId;

/**
 * TableId of the given DeviceId
 */
public class DeviceTableId {
    private DeviceId deviceId;
    private int tableId;
    public DeviceTableId(DeviceId deviceId, int tableid) {
        this.deviceId  = deviceId;
        this.tableId = tableid;
    }
    public int getTableId() {
        return this.tableId;
    }
    public DeviceId getDeviceId() {
        return this.deviceId;
    }

}
