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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Set;

import static org.onosproject.net.table.FlowTableOperation.Type.*;

/**
 * A batch of flow table operations that are broken into stages.
 * TODO move this up to parent's package
 */
public class FlowTableOperations {

    private final List<Set<FlowTableOperation>> stages;
    private final FlowTableOperationsContext callback; // TODO consider Optional

    private FlowTableOperations(List<Set<FlowTableOperation>> stages,
                                FlowTableOperationsContext cb) {
        this.stages = stages;
        this.callback = cb;
    }

    // kryo-constructor
    protected FlowTableOperations() {
        this.stages = Lists.newArrayList();
        this.callback = null;
    }

    /**
     * Returns the flow table operations as sets of stages that should be
     * executed sequentially.
     *
     * @return flow table stages
     */
    public List<Set<FlowTableOperation>> stages() {
        return stages;
    }

    /**
     * Returns the callback for this batch of operations.
     *
     * @return callback
     */
    public FlowTableOperationsContext callback() {
        return callback;
    }

    /**
     * Returns a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("stages", stages)
                .toString();
    }

    /**
     * A builder for constructing flow table operations.
     */
    public static final class Builder {

        private final ImmutableList.Builder<Set<FlowTableOperation>> listBuilder = ImmutableList.builder();
        private ImmutableSet.Builder<FlowTableOperation> currentStage = ImmutableSet.builder();

        // prevent use of the default constructor outside of this file; use the above method
        private Builder() {}

        /**
         * Appends a flow table add to the current stage.
         *
         * @param flowTable flow table
         * @return this
         */
        public Builder add(FlowTable flowTable) {
            currentStage.add(new FlowTableOperation(flowTable, ADD));
            return this;
        }

        /**
         * Appends an existing flow table to the current stage.
         *
         * @param flowTableOperation flow table operation
         * @return this
         */
        public Builder operation(FlowTableOperation flowTableOperation) {
            currentStage.add(flowTableOperation);
            return this;
        }

        /**
         * Appends a flow table modify to the current stage.
         *
         * @param flowTable flow table
         * @return this
         */
        public Builder modify(FlowTable flowTable) {
            currentStage.add(new FlowTableOperation(flowTable, MODIFY));
            return this;
        }

        /**
         * Appends a flow table remove to the current stage.
         *
         * @param flowtable flow table
         * @return this
         */
        // FIXME this is confusing, consider renaming
        public Builder remove(FlowTable flowtable) {
            currentStage.add(new FlowTableOperation(flowtable, REMOVE));
            return this;
        }

        /**
         * Closes the current stage.
         */
        private void closeStage() {
            ImmutableSet<FlowTableOperation> stage = currentStage.build();
            if (!stage.isEmpty()) {
                listBuilder.add(stage);
            }
        }

        /**
         * Closes the current stage and starts a new one.
         *
         * @return this
         */
        public Builder newStage() {
            closeStage();
            currentStage = ImmutableSet.builder();
            return this;
        }

        /**
         * Builds the immutable flow table operations.
         *
         * @return flow rule operations
         */
        public FlowTableOperations build() {
            return build(null);
        }

        /**
         * Builds the immutable flow table operations.
         *
         * @param cb the callback to call when this operation completes
         * @return flow rule operations
         */
        public FlowTableOperations build(FlowTableOperationsContext cb) {
            closeStage();
            return new FlowTableOperations(listBuilder.build(), cb);
        }
    }
}
