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

import org.onosproject.net.table.FlowTableBatchEntry.FlowTableOperation;

@Deprecated
/**
 * @deprecated in Drake release - no longer a public API
 */
public class FlowTableBatchEntry
        extends BatchOperationEntry<FlowTableOperation, FlowTable> {

    private final Long id; // FIXME: consider using Optional<Long>

    public FlowTableBatchEntry(FlowTableOperation operator, FlowTable target) {
        super(operator, target);
        this.id = null;
    }

    public FlowTableBatchEntry(FlowTableOperation operator, FlowTable target, Long id) {
        super(operator, target);
        this.id = id;
    }

    public Long id() {
        return id;
    }

    public enum FlowTableOperation {
        ADD,
        REMOVE,
        MODIFY
    }

}
