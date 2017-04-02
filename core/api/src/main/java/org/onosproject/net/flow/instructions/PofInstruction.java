package org.onosproject.net.flow.instructions;

import org.onosproject.floodlightpof.protocol.instruction.OFInstruction;

/**
 * Abstraction of a single POF instruction.
 */
public interface PofInstruction extends Instruction {

    OFInstruction instruction();
}