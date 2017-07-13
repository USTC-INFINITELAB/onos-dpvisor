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

import org.onlab.util.Identifier;

/**
 * Representation of a table ID.
 */
public final class FlowTableId extends Identifier<Long> {

    public FlowTableId(long id) {
        super(id);
    }

    /**
     * Creates a table ID from a long value.
     *
     * @param id long value
     * @return table ID
     */
    public static FlowTableId valueOf(long id) {
        return new FlowTableId(id);
    }

    /**
     * Gets the table ID value.
     *
     * @return table ID value as long
     */
    public long value() {
        return this.identifier;
    }

    @Override
    public String toString() {
        return Long.toHexString(identifier);
    }
}
