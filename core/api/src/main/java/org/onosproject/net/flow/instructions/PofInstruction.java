package org.onosproject.net.flow.instructions;

import org.onosproject.floodlightpof.protocol.instruction.OFInstruction;
import org.onosproject.net.meter.Meter;

/**
 * Abstraction of a single POF instruction.
 */
public interface PofInstruction extends Instruction {

    /**
     * Represents the type of pof instruction.
     */
    enum PofInstructionType {

        POF_ACTION,

        CALCULATE_FIELD,

        GOTO_DIRECT_TABLE,

        GOTO_TABLE,

        WRITE_METADATA,

        WRITE_METADATA_FROM_PACKET,

        METER

        //TODO: remaining types
    }

    /**
     * Returns the pof instruction.
     *
     * @return pof instruction
     */
    OFInstruction instruction();

    /**
     * Returns the type of pof instruction.
     *
     * @return type of pof instruction
     */
    PofInstructionType pofInstructionType();
}