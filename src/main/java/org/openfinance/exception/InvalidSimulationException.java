package org.openfinance.exception;

/** A saved simulation must contain the inputs required by its calculator. */
public class InvalidSimulationException extends IllegalArgumentException
        implements LocalizableException {
    public InvalidSimulationException() {
        super("Simulation data is invalid or does not match the simulation type.");
    }

    @Override
    public String getMessageKey() {
        return "error.simulation.data.invalid";
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[0];
    }
}
