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
package org.onosproject.net.table;

import com.google.common.base.MoreObjects;

/**
 * Representation of an operation on a flow table.
 */
public class FlowTableOperation {

    /**
     * Type of flow table operations.
     */
    public enum Type {
        ADD,
        MODIFY,
        REMOVE
    }

    private final FlowTable table;
    private final Type type;

    public FlowTableOperation(FlowTable table, Type type) {
        this.table = table;
        this.type = type;
    }

    /**
     * Returns the type of operation.
     *
     * @return type
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the flow table.
     *
     * @return flow table
     */
    public FlowTable table() {
        return table;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("table", table)
                .add("type", type)
                .toString();
    }
}
