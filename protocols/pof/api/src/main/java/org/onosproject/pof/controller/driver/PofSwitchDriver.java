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

import org.jboss.netty.channel.Channel;
import org.onosproject.floodlightpof.protocol.OFError;
import org.onosproject.floodlightpof.protocol.OFFeaturesReply;
import org.onosproject.floodlightpof.protocol.OFMessage;
import org.onosproject.floodlightpof.protocol.OFPhysicalPort;
import org.onosproject.floodlightpof.protocol.statistics.OFDescriptionStatistics;
import org.onosproject.net.driver.HandlerBehaviour;
import org.onosproject.pof.controller.Dpid;
import org.onosproject.pof.controller.PofSwitch;

import java.util.List;

/**
 * Represents the driver side of an POF switch.
 * This interface should never be exposed to consumers.
 *
 */
public interface PofSwitchDriver extends PofSwitch, HandlerBehaviour {

    /**
     * Sets the POF agent to be used. This method
     * can only be called once.
     * @param agent the agent to set.
     */
    void setAgent(PofAgent agent);

    /**
     * Sets the Role handler object.
     * This method can only be called once.
     * @param roleHandler the roleHandler class
     */
    void setRoleHandler(RoleHandler roleHandler);

    /**
     * Reasserts this controllers role to the switch.
     * Useful in cases where the switch no longer agrees
     * that this controller has the role it claims.
     */
    void reassertRole();

    /**
     * Handle the situation where the role request triggers an error.
     * @param error the error to handle.
     * @return true if handled, false if not.
     */
    boolean handleRoleError(OFError error);

    /**
     * If this driver know of Nicira style role messages, these should
     * be handled here.
     * @param m the role message to handle.
     * @throws SwitchStateException if the message received was
     *  not a nicira role or was malformed.
     */
    void handleNiciraRole(OFMessage m) throws SwitchStateException;

    /**
     * Handle OF 1.x (where x &gt; 0) role messages.
     * @param m the role message to handle
     * @throws SwitchStateException if the message received was
     *  not a nicira role or was malformed.
     */
    void handleRole(OFMessage m) throws SwitchStateException;

    /**
     * Announce to the POF agent that this switch has connected.
     * @return true if successful, false if duplicate switch.
     */
    boolean connectSwitch();

    /**
     * Announce to the POF agent that this switch has connected successfully.
     * @return true if successful, false if duplicate switch.
     */
    void handleConnectionUp();

    /**
     * Activate this MASTER switch-controller relationship in the OF agent.
     * @return true is successful, false is switch has not
     * connected or is unknown to the system.
     */
    boolean activateMasterSwitch();

    /**
     * Activate this EQUAL switch-controller relationship in the OF agent.
     * @return true is successful, false is switch has not
     * connected or is unknown to the system.
     */
    boolean activateEqualSwitch();

    /**
     * Transition this switch-controller relationship to an EQUAL state.
     */
    void transitionToEqualSwitch();

    /**
     * Transition this switch-controller relationship to an Master state.
     */
    void transitionToMasterSwitch();

    /**
     * Remove this switch from the openflow agent.
     */
    void removeConnectedSwitch();

    /**
     * Sets the ports on this switch.
     * @param port the port set and descriptions
     */
    //wenjian
    //void setPortDescReply(OFPortDescStatsReply portDescReply);
    //
    void setPort(OFPhysicalPort port);
    //wenjian
    /**
     * Sets the ports on this switch.
     * @param ports list of port set and descriptions
     */
    //wenjian
    //void setPortDescReplies(List<OFPortDescStatsReply> portDescReplies);
    //
    void setPorts(List<OFPhysicalPort> ports);
    //wenjian
    /**
     * Sets the features reply for this switch.
     * @param featuresReply the features to set.
     */
    void setFeaturesReply(OFFeaturesReply featuresReply);

    /**
     * Sets the switch description.
     * @param desc the descriptions
     */
    //wenjian
    //void setSwitchDescription(OFDescStatsReply desc);
    //
    void setSwitchProperties(OFDescriptionStatistics desc);
    //wenjian
    /**
     * Gets the next transaction id to use.
     * @return the xid
     */
    int getNextTransactionId();


    /**
     * Sets the OF version for this switch.
     * @param ofV the version to set.
     */
    //void setOFVersion(OFVersion ofV);//wenjian

    /**
     * Sets this switch has having a full flowtable.
     * @param full true if full, false otherswise.
     */
    void setTableFull(boolean full);

    /**
     * Sets the associated Netty channel for this switch.
     * @param channel the Netty channel
     */
    void setChannel(Channel channel);

    /**
     * Sets whether the switch is connected.
     *
     * @param connected whether the switch is connected
     */
    void setConnected(boolean connected);

    /**
     * Initialises the behaviour.
     * @param dpid a dpid
     * @param desc a switch description
     * @param
     */
    //wenjian
    //void init(Dpid dpid, OFDescStatsReply desc, OFVersion ofv);
    //
    void init(Dpid dpid, OFDescriptionStatistics desc);
    /**
     * Does this switch support Nicira Role messages.
     * @return true if supports, false otherwise.
     */
    Boolean supportNxRole();


    /**
     * Starts the driver specific handshake process.
     */
    void startDriverHandshake();

    /**
     * Checks whether the driver specific handshake is complete.
     * @return true is finished, false if not.
     */
    boolean isDriverHandshakeComplete();

    /**
     * Process a message during the driver specific handshake.
     * @param m the message to process.
     */
    void processDriverHandshakeMessage(OFMessage m);

    /**
     * Sends only role request messages.
     *
     * @param message a role request message.
     */
    void sendRoleRequest(OFMessage message);

    /**
     * Allows the handshaker behaviour to send messages during the
     * handshake phase only.
     *
     * @param message an OpenFlow message
     */
    void sendHandshakeMessage(OFMessage message);
}
