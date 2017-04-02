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

import org.onosproject.event.AbstractEvent;

/**
 * Describes flow table event.
 */
public class FlowTableEvent extends AbstractEvent<FlowTableEvent.Type, FlowTable> {

    /**
     * Type of flow table events.
     */
    public enum Type {
        /**
         * Signifies that a new flow table has been detected.
         */
        TABLE_ADDED,

        /**
         * Signifies that a flow table has been removed.
         */
        TABLE_REMOVED,

        /**
         * Signifies that a table has been updated.
         */
        TABLE_UPDATED,

        // internal event between Manager <-> Store

        /*
         * Signifies that a request to add flow table has been added to the store.
         */
        TABLE_ADD_REQUESTED,
        /*
         * Signifies that a request to remove flow table has been added to the store.
         */
        TABLE_REMOVE_REQUESTED,
    }

    /**
     * Creates an event of a given type and for the specified flow table and the
     * current time.
     *
     * @param type     flow table event type
     * @param table event flow table subject
     */
    public FlowTableEvent(Type type, FlowTable table) {
        super(type, table);
    }

    /**
     * Creates an event of a given type and for the specified flow table and time.
     *
     * @param type     flow table event type
     * @param table event flow table subject
     * @param time     occurrence time
     */
    public FlowTableEvent(Type type, FlowTable table, long time) {
        super(type, table, time);
    }

}
