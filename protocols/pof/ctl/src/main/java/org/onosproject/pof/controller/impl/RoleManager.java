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
package org.onosproject.pof.controller.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.onosproject.floodlightpof.protocol.OFError;
import org.onosproject.floodlightpof.protocol.OFExperimenter;
import org.onosproject.floodlightpof.protocol.OFRoleReply;
import org.onosproject.floodlightpof.protocol.OFRoleRequest;
import org.onosproject.floodlightpof.protocol.OFControllerRole;
import org.onosproject.pof.controller.RoleState;
import org.onosproject.pof.controller.driver.RoleHandler;
import org.onosproject.pof.controller.driver.RoleRecvStatus;
import org.onosproject.pof.controller.driver.PofSwitchDriver;
import org.onosproject.pof.controller.driver.RoleReplyInfo;
import org.onosproject.pof.controller.driver.SwitchStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
/**
 * A utility class to handle role requests and replies for this channel.
 * After a role request is submitted the role changer keeps track of the
 * pending request, collects the reply (if any) and times out the request
 * if necessary.
 */
class RoleManager implements RoleHandler {
    protected static final long NICIRA_EXPERIMENTER = 0x2320;

    private static Logger log = LoggerFactory.getLogger(RoleManager.class);

    // The time until cached XID is evicted. Arbitrary for now.
    private final int pendingXidTimeoutSeconds = 60;

    // The cache for pending expected RoleReplies keyed on expected XID
    private Cache<Integer, RoleState> pendingReplies =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(pendingXidTimeoutSeconds, TimeUnit.SECONDS)
                    .build();

    // the expectation set by the caller for the returned role
    private RoleRecvStatus expectation;
    private final PofSwitchDriver sw;


    public RoleManager(PofSwitchDriver sw) {
        this.expectation = RoleRecvStatus.MATCHED_CURRENT_ROLE;
        this.sw = sw;
    }
    private int sendPofRoleRequest(RoleState role) throws IOException {
        // Convert the role enum to the appropriate role to send
        OFControllerRole roleToSend;
        roleToSend = OFControllerRole.ROLE_NOCHANGE;
        switch (role) {
            case EQUAL:
                roleToSend = OFControllerRole.ROLE_EQUAL;
                break;
            case MASTER:
                roleToSend = OFControllerRole.ROLE_MASTER;
                break;
            case SLAVE:
                roleToSend = OFControllerRole.ROLE_SLAVE;
                break;
            default:
                log.warn("Sending default role.noChange to switch {}."
                                 + " Should only be used for queries.", sw);
        }

        OFRoleRequest rrm = new OFRoleRequest();
        rrm.setOfControllerRole(roleToSend);
        sw.sendRoleRequest(rrm);
        int xid = rrm.getXid();
        log.info("OFRoleRequest {}", rrm.toString());
        return xid;
    }
    @Override
    public synchronized boolean sendRoleRequest(RoleState role, RoleRecvStatus exp)
            throws IOException {
        this.expectation = exp;
        int xid = sendPofRoleRequest(role);
        pendingReplies.put(xid, role);
        log.info("PendingReplies xid={},role={}", xid, role);
        return true;
    }
    private void handleUnsentRoleMessage(RoleState role,
                                         RoleRecvStatus exp) throws IOException {
        // typically this is triggered for a switch where role messages
        // are not supported - we confirm that the role being set is
        // master
        if (exp != RoleRecvStatus.MATCHED_SET_ROLE) {

            log.error("Expected MASTER role from registry for switch "
                              + "which has no support for role-messages."
                              + "Received {}. It is possible that this switch "
                              + "is connected to other controllers, in which "
                              + "case it should support role messages - not "
                              + "moving forward.", role);

        }

    }



    @Override
    public synchronized RoleRecvStatus deliverRoleReply(RoleReplyInfo rri)
            throws SwitchStateException {
        int xid = (int) rri.getXid();
        RoleState receivedRole = rri.getRole();
        RoleState expectedRole = pendingReplies.getIfPresent(xid);
        log.info("deliverRoleReply receivedRole={}, xid={}, expectedRole={}", receivedRole, xid, expectedRole);
        if (expectedRole == null) {
            RoleState currentRole = (sw != null) ? sw.getRole() : null;
            if (currentRole != null) {
                if (currentRole == rri.getRole()) {
                    // Don't disconnect if the role reply we received is
                    // for the same role we are already in.
                    // FIXME: but we do from the caller anyways.
                    log.info("Received unexpected RoleReply from "
                                     + "Switch: {}. "
                                     + "Role in reply is same as current role of this "
                                     + "controller for this sw. Ignoring ...",
                             sw.getStringId());
                    return RoleRecvStatus.OTHER_EXPECTATION;
                } else {
                    String msg = String.format("Switch: [%s], "
                                                       + "received unexpected RoleReply[%s]. "
                                                       + "No roles are pending, and this controller's "
                                                       + "current role:[%s] does not match reply. "
                                                       + "Disconnecting switch ... ",
                                               sw.getStringId(),
                                               rri, currentRole);
                    throw new SwitchStateException(msg);
                }
            }
            log.info("Received unexpected RoleReply {} from "
                             + "Switch: {}. "
                             + "This controller has no current role for this sw. "
                             + "Ignoring ...",
                     rri,
                     sw == null ? "(null)" : sw.getStringId());
            return RoleRecvStatus.OTHER_EXPECTATION;
        }

        // XXX Should check generation id meaningfully and other cases of expectations
        //if (pendingXid != xid) {
        //    log.info("Received older role reply from " +
        //            "switch {} ({}). Ignoring. " +
        //            "Waiting for {}, xid={}",
        //            new Object[] {sw.getStringId(), rri,
        //            pendingRole, pendingXid });
        //    return RoleRecvStatus.OLD_REPLY;
        //}
        sw.returnRoleReply(expectedRole, receivedRole);

        if (expectedRole == receivedRole) {
            log.info("Received role reply message from {} that matched "
                             + "expected role-reply {} with expectations {}",
                     sw.getStringId(), receivedRole, expectation);

            // Done with this RoleReply; Invalidate
            pendingReplies.invalidate(xid);
            if (expectation == RoleRecvStatus.MATCHED_CURRENT_ROLE ||
                    expectation == RoleRecvStatus.MATCHED_SET_ROLE) {
                return expectation;
            } else {
                return RoleRecvStatus.OTHER_EXPECTATION;
            }
        }

        pendingReplies.invalidate(xid);
        // if xids match but role's don't, perhaps its a query (OF1.3)
        if (expectation == RoleRecvStatus.REPLY_QUERY) {
            return expectation;
        }

        return RoleRecvStatus.OTHER_EXPECTATION;
    }

    /**
     * Called if we receive an  error message. If the xid matches the
     * pending request we handle it otherwise we ignore it.
     *
     * Note: since we only keep the last pending request we might get
     * error messages for earlier role requests that we won't be able
     * to handle
     */
    @Override
    public synchronized RoleRecvStatus deliverError(OFError error)
            throws SwitchStateException {
        RoleState errorRole = pendingReplies.getIfPresent(error.getXid());
        if (errorRole == null) {
            if (error.getErrorType() == OFError.OFErrorType.OFPET_ROLE_REQUEST_FAILED.getValue()) {
                log.debug("Received an error msg from sw {} for a role request,"
                                  + " but not for pending request in role-changer; "
                                  + " ignoring error {} ...",
                          sw.getStringId(), error);
            } else {
                log.debug("Received an error msg from sw {}, but no pending "
                                  + "requests in role-changer; not handling ...",
                          sw.getStringId());
            }
            return RoleRecvStatus.OTHER_EXPECTATION;
        }
        // it is an error related to a currently pending role request message
        if (error.getErrorType() == OFError.OFErrorType.OFPET_BAD_REQUEST.getValue()) {
            log.error("Received a error msg {} from sw {} for "
                              + "pending role request {}. Switch driver indicates "
                              + "role-messaging is supported. Possible issues in "
                              + "switch driver configuration?",
                      (error).toString(),
                      sw.getStringId(),
                      errorRole);
            return RoleRecvStatus.UNSUPPORTED;
        }

        if (error.getErrorType() == OFError.OFErrorType.OFPET_ROLE_REQUEST_FAILED.getValue()) {
            log.info("OFPET_ROLE_REQUEST_FAILED");
        }

        // This error message was for a role request message but we dont know
        // how to handle errors for nicira role request messages
        return RoleRecvStatus.OTHER_EXPECTATION;
    }

    /**
     * Extract the role from an OFVendor message.
     *
     * Extract the role from an OFVendor message if the message is a
     * Nicira role reply. Otherwise return null.
     *
     * @param experimenterMsg message
     * @return The role in the message if the message is a Nicira role
     * reply, null otherwise.
     * @throws SwitchStateException If the message is a Nicira role reply
     * but the numeric role value is unknown.
     */
    @Override
    public RoleState extractNiciraRoleReply(OFExperimenter experimenterMsg)
            throws SwitchStateException {
        int vendor = (int) experimenterMsg.getExperimenter();
        if (vendor != 0x2320) {
            return null;
        }
        return null;
    }

    /**
     * Extract the role information from an OF1.3 Role Reply Message.
     *
     * @param rrmsg the role message
     * @return RoleReplyInfo object
     * @throws SwitchStateException if the role information could not be extracted.
     */
    @Override
    public RoleReplyInfo extractOFRoleReply(OFRoleReply rrmsg)
            throws SwitchStateException {

        OFControllerRole cr = rrmsg.getOfControllerRole();
        //OFControllerRole cr = OFControllerRole.ROLE_MASTER;
        RoleState role = null;
        switch (cr) {
            case ROLE_EQUAL:
                role = RoleState.EQUAL;
                break;
            case ROLE_MASTER:
                role = RoleState.MASTER;
                break;
            case ROLE_SLAVE:
                role = RoleState.SLAVE;
                break;
            case ROLE_NOCHANGE: // switch should send current role
            default:
                String msg = String.format("Unknown controller role %s "
                                                   + "received from switch %s", cr, sw);
                throw new SwitchStateException(msg);
        }
        RoleReplyInfo roleReplyInfo = new RoleReplyInfo(role, rrmsg.getXid());
        log.info("extractOFRoleReply role={}, xid={}", role, rrmsg.getXid());
        return roleReplyInfo;
    }

}

