package br.edu.rotavital.dto;

/**
 * Payload de entrada para os endpoints de benchmark.
 *
 * @param dataSize          Número de requisições de sangue a processar (ex: 100000, 1000000)
 * @param threads           Número de threads paralelas (ignorado no endpoint sequencial)
 * @param criticalThreshold Percentual mínimo de cobertura abaixo do qual a requisição é crítica (0.0 a 1.0)
 * @param useVirtualThreads Se true, usa Virtual Threads do Java 21 (Project Loom) em vez de platform threads
 */
public record BenchmarkRequest(
        int dataSize,
        int threads,
        double criticalThreshold,
        boolean useVirtualThreads
) {
    /**
     * Valores padrão para quando o cliente não especificar todos os campos.
     */
    public BenchmarkRequest {
        if (dataSize <= 0) dataSize = 100_000;
        if (threads <= 0) threads = 4;
        if (criticalThreshold <= 0 || criticalThreshold > 1) criticalThreshold = 0.20;
    }
}
