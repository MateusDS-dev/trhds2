package br.edu.rotavital.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload de resposta dos endpoints de benchmark.
 *
 * Ambas as versões (sequencial e paralela) retornam este mesmo DTO,
 * permitindo comparação direta dos resultados para verificar ausência
 * de race conditions (se os valores diferem, há bug de concorrência).
 *
 * @param version                  "sequential" | "parallel" | "parallel-virtual"
 * @param threads                  Número de threads usadas (1 para sequencial)
 * @param dataSize                 Total de requisições processadas
 * @param elapsedMs                Tempo de processamento em milissegundos
 * @param criticalRequests         Requisições com cobertura abaixo do threshold
 * @param totalCompatibilityScore  Soma das coberturas de todas as requisições (verificação de consistência)
 * @param coverageByBloodType      Cobertura média por tipo sanguíneo
 * @param speedupVsSequential      Speedup = T_seq / T_parallel (null na resposta sequencial)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenchmarkResponse(
        String version,
        int threads,
        int dataSize,
        long elapsedMs,
        long criticalRequests,
        double totalCompatibilityScore,
        java.util.Map<String, Double> coverageByBloodType,
        Double speedupVsSequential
) {}
