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
package org.onosproject.pof.controller.driver;


/*wenjian
import org.onosproject.openflow.controller.RoleState;
import org.projectfloodlight.openflow.types.U64;
*/

import org.onosproject.floodlightpof.util.U64;
import org.onosproject.pof.controller.RoleState;

/**
 * Helper class returns role reply information in the format understood
 * by the controller.
 */
public class RoleReplyInfo {
    private final RoleState role;
    private final long xid;

    public RoleReplyInfo(RoleState role, long xid) {
        this.role = role;
        this.xid = xid;
    }
    public RoleState getRole() {
        return role;
    }
    public long getXid() {
        return xid;
    }
    @Override
    public String toString() {
        return "[Role:" + role + " Xid:" + xid + "]";
    }
}
