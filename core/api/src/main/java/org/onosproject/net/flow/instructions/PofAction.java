package org.onosproject.net.flow.instructions;

import org.onosproject.floodlightpof.protocol.action.OFAction;

/**
 * Abstraction of a single POF action.
 */
public interface PofAction extends Instruction {

    OFAction action();
}
