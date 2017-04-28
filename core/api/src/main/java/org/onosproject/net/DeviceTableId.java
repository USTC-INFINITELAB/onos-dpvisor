package org.onosproject.net;

/**
 * Created by niubin on 17-4-23.
 */
public class DeviceTableId {
    public DeviceId deviceId;
    public int tableid;
    public DeviceTableId(DeviceId deviceId, int tableid) {
        this.deviceId  = deviceId;
        this.tableid = tableid;
    }
    public int getTableid() {
        return this.tableid;
    }
    public DeviceId getDeviceId() {
        return this.deviceId;
    }

}
