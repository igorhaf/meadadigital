package com.meada.knowledge;

/**
 * Conversão de {@code float[]} para o literal textual que o pgvector aceita em
 * {@code ?::vector} — ex.: {@code [0.1,0.2,0.3]}. Evita puxar a dependência pgvector-java
 * só para isto: o JdbcTemplate manda a string e o cast {@code ::vector} no SQL resolve.
 */
final class VectorLiterals {

    private VectorLiterals() {
    }

    static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8 + 2).append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
