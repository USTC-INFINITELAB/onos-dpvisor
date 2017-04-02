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

/**
 * The context of a flow table operations that will become the subject of
 * the notification.
 *
 * Implementations of this class must be serializable.
 */
public interface FlowTableOperationsContext {
    // TODO we might also want to execute a method on behalf of the app
    default void onSuccess(FlowTableOperations ops){}
    default void onError(FlowTableOperations ops){}
}
