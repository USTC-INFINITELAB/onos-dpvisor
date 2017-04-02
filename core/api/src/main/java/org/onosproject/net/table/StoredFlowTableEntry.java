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


public interface StoredFlowTableEntry extends FlowTableEntry {

    /**
     * Sets the last active epoch time.
     */
    void setLastSeen();

    /**
     * Sets the new state for this table.
     * @param newState new flow table state.
     */
    void setState(FlowTableState newState);

    /**
     * Sets how long this table has been entered in the system.
     * @param life epoch time
     */
    void setLife(long life);

    /**
     * Number of packets seen by this table.
     * @param packets a long value
     */
    void setPackets(long packets);

    /**
     * Number of bytes seen by this table.
     * @param bytes a long value
     */
    void setBytes(long bytes);

}
