package com.meada.admin.instances;

/**
 * Falha ao falar com a API de instância da Evolution. Carrega o status HTTP original quando
 * houver (o controller distingue o 400 de "nome duplicado" dos demais erros).
 */
public class EvolutionInstanceException extends RuntimeException {

    private final int evolutionStatus;

    public EvolutionInstanceException(String message) {
        this(message, null, 0);
    }

    public EvolutionInstanceException(String message, Throwable cause) {
        this(message, cause, 0);
    }

    public EvolutionInstanceException(String message, Throwable cause, int evolutionStatus) {
        super(message, cause);
        this.evolutionStatus = evolutionStatus;
    }

    /** Status HTTP devolvido pela Evolution, ou 0 se a falha foi de rede/parse. */
    public int evolutionStatus() {
        return evolutionStatus;
    }
}
