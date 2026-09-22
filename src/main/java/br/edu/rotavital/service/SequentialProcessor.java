package br.edu.rotavital.service;

import br.edu.rotavital.model.BloodRequest;
import br.edu.rotavital.model.BloodType;
import br.edu.rotavital.model.DonorStock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Processador SEQUENCIAL de validação em lote de compatibilidade.
 *
 * Itera sobre todas as N requisições em uma única thread, calculando
 * a cobertura de estoque para cada uma sequencialmente.
 *
 * Complexidade: O(N × K) onde:
 *   N = número de requisições
 *   K = constante (8 tipos sanguíneos — tamanho fixo do enum)
 * → Simplifica para O(N) na análise assintótica.
 *
 * Esta é a versão de referência (baseline) para comparação de speedup.
 */
@Service
public class SequentialProcessor {

    private static final Logger log = LoggerFactory.getLogger(SequentialProcessor.class);

    /**
     * Resultado parcial ou total do processamento.
     *
     * @param criticalCount        Requisições críticas encontradas
     * @param totalCoverageScore   Soma de todas as coberturas (para calcular média)
     * @param coverageSumByType    Acumulador de cobertura por tipo sanguíneo
     * @param countByType          Contador de requisições por tipo (para calcular médias)
     */
    public record ProcessingResult(
            long criticalCount,
            double totalCoverageScore,
            Map<BloodType, Double> coverageSumByType,
            Map<BloodType, Long> countByType
    ) {
        /**
         * Cria um resultado vazio (zero-value) para inicialização de accumuladores.
         */
        public static ProcessingResult empty() {
            Map<BloodType, Double> coverageMap = new EnumMap<>(BloodType.class);
            Map<BloodType, Long> countMap = new EnumMap<>(BloodType.class);
            for (BloodType bt : BloodType.values()) {
                coverageMap.put(bt, 0.0);
                countMap.put(bt, 0L);
            }
            return new ProcessingResult(0L, 0.0, coverageMap, countMap);
        }

        /**
         * Merge imutável de dois resultados parciais.
         * Usado no passo de agregação do processador paralelo.
         * Complexidade: O(K) onde K = 8 tipos sanguíneos.
         */
        public ProcessingResult merge(ProcessingResult other) {
            Map<BloodType, Double> mergedCoverage = new EnumMap<>(BloodType.class);
            Map<BloodType, Long> mergedCount = new EnumMap<>(BloodType.class);
            for (BloodType bt : BloodType.values()) {
                mergedCoverage.put(bt,
                        this.coverageSumByType.getOrDefault(bt, 0.0) +
                        other.coverageSumByType.getOrDefault(bt, 0.0));
                mergedCount.put(bt,
                        this.countByType.getOrDefault(bt, 0L) +
                        other.countByType.getOrDefault(bt, 0L));
            }
            return new ProcessingResult(
                    this.criticalCount + other.criticalCount,
                    this.totalCoverageScore + other.totalCoverageScore,
                    mergedCoverage,
                    mergedCount
            );
        }
    }

    /**
     * Processa uma fatia (slice) de requisições sequencialmente.
     *
     * Recebe fromIndex (inclusive) e toIndex (exclusive) para permitir
     * reuso desta lógica tanto no processamento completo quanto por cada
     * thread paralela que recebe sua própria fatia.
     *
     * @param requests   Lista completa de requisições (acesso por índice é O(1))
     * @param fromIndex  Início da fatia (inclusive)
     * @param toIndex    Fim da fatia (exclusive)
     * @param stockMap   Mapa de estoque (somente-leitura)
     * @param threshold  Limite para classificar como crítico
     * @return ProcessingResult com os acumuladores da fatia
     */
    public ProcessingResult processSlice(
            List<BloodRequest> requests,
            int fromIndex,
            int toIndex,
            Map<BloodType, DonorStock> stockMap,
            double threshold) {

        long criticalCount = 0L;
        double totalCoverageScore = 0.0;
        Map<BloodType, Double> coverageSumByType = new EnumMap<>(BloodType.class);
        Map<BloodType, Long> countByType = new EnumMap<>(BloodType.class);

        for (BloodType bt : BloodType.values()) {
            coverageSumByType.put(bt, 0.0);
            countByType.put(bt, 0L);
        }

        // ── Núcleo do algoritmo: O(N) no total, O(N/T) por fatia ────────────
        for (int i = fromIndex; i < toIndex; i++) {
            BloodRequest req = requests.get(i);
            double coverage = CompatibilityService.computeCoverage(req, stockMap);

            totalCoverageScore += coverage;
            coverageSumByType.merge(req.requiredType(), coverage, Double::sum);
            countByType.merge(req.requiredType(), 1L, Long::sum);

            if (CompatibilityService.isCritical(coverage, threshold, req.urgency())) {
                criticalCount++;
            }
        }
        // ────────────────────────────────────────────────────────────────────

        return new ProcessingResult(criticalCount, totalCoverageScore,
                coverageSumByType, countByType);
    }

    /**
     * Processa TODAS as requisições em uma única thread (versão de referência).
     *
     * @param requests  Lista de requisições
     * @param stockMap  Estoque nacional
     * @param threshold Limiar de criticidade
     * @return Resultado completo do processamento
     */
    public ProcessingResult processAll(
            List<BloodRequest> requests,
            Map<BloodType, DonorStock> stockMap,
            double threshold) {

        log.debug("Iniciando processamento SEQUENCIAL de {} requisições", requests.size());
        return processSlice(requests, 0, requests.size(), stockMap, threshold);
    }
}
