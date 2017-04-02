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
package org.onosproject.net.table;

import org.slf4j.Logger;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.slf4j.LoggerFactory.getLogger;

public class DefaultFlowTableEntry extends DefaultFlowTable
    implements StoredFlowTableEntry {

    private static final Logger log = getLogger(DefaultFlowTableEntry.class);

    private long life;
    private long packets;
    private long bytes;
    private FlowTableState state;

    private long lastSeen = -1;

    private final int errType;

    private final int errCode;

    /**
     * Creates a flow entry of flow table specified with the table state
     * and statistic information.
     *
     * @param table the flow table
     * @param state the flow state
     * @param life the duration second of flow
     * @param packets the number of packets of this flow
     * @param bytes the the number of bytes of this flow
     */
    public DefaultFlowTableEntry(FlowTable table, FlowTableState state,
                                 long life, long packets, long bytes) {
        super(table);
        this.state = state;
        this.life = life;
        this.packets = packets;
        this.bytes = bytes;
        this.errCode = -1;
        this.errType = -1;
        this.lastSeen = System.currentTimeMillis();
    }

    /**
     * Creates a flow entry of flow table.
     *
     * @param table the flow table
     */
    public DefaultFlowTableEntry(FlowTable table) {
        super(table);
        this.state = FlowTableState.PENDING_ADD;
        this.life = 0;
        this.packets = 0;
        this.bytes = 0;
        this.errCode = -1;
        this.errType = -1;
        this.lastSeen = System.currentTimeMillis();
    }

    /**
     * Creates a flow entry of flow table specified with the table state
     * and statistic information.
     *
     * @param table the flow table
     * @param errType the error type
     * @param errCode the error code
     */
    public DefaultFlowTableEntry(FlowTable table, int errType, int errCode) {
        super(table);
        this.state = FlowTableState.FAILED;
        this.errType = errType;
        this.errCode = errCode;
        this.lastSeen = System.currentTimeMillis();
    }

    @Override
    public long life() {
        return life;
    }

    @Override
    public long packets() {
        return packets;
    }

    @Override
    public long bytes() {
        return bytes;
    }

    @Override
    public FlowTableState state() {
        return this.state;
    }

    @Override
    public long lastSeen() {
        return lastSeen;
    }

    @Override
    public void setLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    @Override
    public void setState(FlowTableState newState) {
        this.state = newState;
    }

    @Override
    public void setLife(long life) {
        this.life = life;
    }

    @Override
    public void setPackets(long packets) {
        this.packets = packets;
    }

    @Override
    public void setBytes(long bytes) {
        this.bytes = bytes;
    }

    @Override
    public int errType() {
        return this.errType;
    }

    @Override
    public int errCode() {
        return this.errCode;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("rule", super.toString())
                .add("state", state)
                .toString();
    }
}
