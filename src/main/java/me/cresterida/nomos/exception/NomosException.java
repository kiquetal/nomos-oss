package me.cresterida.nomos.exception;

public class NomosException extends RuntimeException {

    private final String error;

    public NomosException(String error, String message) {
        super(message);
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
