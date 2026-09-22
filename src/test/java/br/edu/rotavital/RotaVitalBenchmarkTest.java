package br.edu.rotavital;

import br.edu.rotavital.model.BloodRequest;
import br.edu.rotavital.model.BloodType;
import br.edu.rotavital.model.DonorStock;
import br.edu.rotavital.service.DataGeneratorService;
import br.edu.rotavital.service.ParallelProcessor;
import br.edu.rotavital.service.SequentialProcessor;
import br.edu.rotavital.service.SequentialProcessor.ProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes do módulo de paralelismo do Rota Vital.
 *
 * Objetivo principal: garantir que o resultado paralelo é IDÊNTICO ao sequencial
 * para qualquer número de threads — o que prova ausência de race conditions.
 */
@SpringBootTest
class RotaVitalBenchmarkTest {

    @Autowired
    private DataGeneratorService dataGenerator;

    @Autowired
    private SequentialProcessor sequentialProcessor;

    @Autowired
    private ParallelProcessor parallelProcessor;

    private List<BloodRequest> requests;
    private Map<BloodType, DonorStock> stockMap;
    private static final double THRESHOLD = 0.20;
    private static final int DATA_SIZE = 10_000; // pequeno para testes unitários rápidos

    @BeforeEach
    void setUp() {
        requests = dataGenerator.getOrGenerateRequests(DATA_SIZE);
        stockMap = dataGenerator.getStockMap();
    }

    // ── Testes de Correção (ausência de race condition) ───────────────────────

    @ParameterizedTest(name = "threads = {0}")
    @ValueSource(ints = {1, 2, 4, 8})
    @DisplayName("Resultado paralelo deve ser IDÊNTICO ao sequencial (sem race condition)")
    void parallelResultMustMatchSequential(int numThreads) {
        ProcessingResult sequential = sequentialProcessor.processAll(requests, stockMap, THRESHOLD);
        ProcessingResult parallel = parallelProcessor.processParallel(
                requests, stockMap, THRESHOLD, numThreads, false);

        assertThat(parallel.criticalCount())
                .as("criticalCount deve ser igual para %d threads", numThreads)
                .isEqualTo(sequential.criticalCount());

        assertThat(parallel.totalCoverageScore())
                .as("totalCoverageScore deve ser igual para %d threads", numThreads)
                .isCloseTo(sequential.totalCoverageScore(), within(0.001));

        // Verifica cobertura por tipo sanguíneo
        for (BloodType bt : BloodType.values()) {
            assertThat(parallel.coverageSumByType().get(bt))
                    .as("coverageSumByType[%s] deve ser igual para %d threads", bt, numThreads)
                    .isCloseTo(sequential.coverageSumByType().get(bt), within(0.001));

            assertThat(parallel.countByType().get(bt))
                    .as("countByType[%s] deve ser igual para %d threads", bt, numThreads)
                    .isEqualTo(sequential.countByType().get(bt));
        }
    }

    @Test
    @DisplayName("Virtual Threads (Java 21) devem produzir resultado idêntico ao sequencial")
    void virtualThreadsResultMustMatchSequential() {
        ProcessingResult sequential = sequentialProcessor.processAll(requests, stockMap, THRESHOLD);
        ProcessingResult virtualParallel = parallelProcessor.processParallel(
                requests, stockMap, THRESHOLD, 8, true); // useVirtualThreads = true

        assertThat(virtualParallel.criticalCount())
                .isEqualTo(sequential.criticalCount());
        assertThat(virtualParallel.totalCoverageScore())
                .isCloseTo(sequential.totalCoverageScore(), within(0.001));
    }

    // ── Testes de Compatibilidade Sanguínea ──────────────────────────────────

    @Test
    @DisplayName("O- deve aceitar apenas doadores O-")
    void oNegativeAcceptsOnlyONegative() {
        assertThat(BloodType.O_NEGATIVE.accepts(BloodType.O_NEGATIVE)).isTrue();
        assertThat(BloodType.O_NEGATIVE.accepts(BloodType.O_POSITIVE)).isFalse();
        assertThat(BloodType.O_NEGATIVE.accepts(BloodType.A_POSITIVE)).isFalse();
    }

    @Test
    @DisplayName("AB+ deve aceitar todos os tipos sanguíneos")
    void abPositiveAcceptsAll() {
        for (BloodType donorType : BloodType.values()) {
            assertThat(BloodType.AB_POSITIVE.accepts(donorType))
                    .as("AB+ deve aceitar doador do tipo %s", donorType)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("O- não deve aceitar tipos positivos como doadores")
    void oNegativeRejectsPositiveTypes() {
        assertThat(BloodType.O_NEGATIVE.accepts(BloodType.A_POSITIVE)).isFalse();
        assertThat(BloodType.O_NEGATIVE.accepts(BloodType.B_POSITIVE)).isFalse();
        assertThat(BloodType.O_NEGATIVE.accepts(BloodType.AB_POSITIVE)).isFalse();
    }

    // ── Testes de Dados ──────────────────────────────────────────────────────

    @Test
    @DisplayName("DataGenerator deve gerar o número correto de requisições")
    void dataGeneratorProducesCorrectSize() {
        assertThat(requests).hasSize(DATA_SIZE);
    }

    @Test
    @DisplayName("Todas as requisições devem ter tipo sanguíneo e urgência definidos")
    void allRequestsHaveRequiredFields() {
        for (BloodRequest req : requests) {
            assertThat(req.requiredType()).isNotNull();
            assertThat(req.urgency()).isNotNull();
            assertThat(req.unitsNeeded()).isGreaterThan(0);
            assertThat(req.hospitalId()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Estoque deve conter todos os 8 tipos sanguíneos")
    void stockContainsAllBloodTypes() {
        assertThat(stockMap).hasSize(BloodType.values().length);
        for (BloodType bt : BloodType.values()) {
            assertThat(stockMap).containsKey(bt);
            assertThat(stockMap.get(bt).unitsAvailable()).isGreaterThan(0);
        }
    }

    // ── Testes de Resultados Parciais (merge) ─────────────────────────────────

    @Test
    @DisplayName("Merge de dois resultados vazios deve produzir resultado vazio")
    void mergeOfEmptyResultsIsEmpty() {
        ProcessingResult a = ProcessingResult.empty();
        ProcessingResult b = ProcessingResult.empty();
        ProcessingResult merged = a.merge(b);

        assertThat(merged.criticalCount()).isZero();
        assertThat(merged.totalCoverageScore()).isZero();
    }

    @Test
    @DisplayName("Soma dos countByType deve ser igual ao dataSize total")
    void countByTypeSumEqualsDataSize() {
        ProcessingResult result = sequentialProcessor.processAll(requests, stockMap, THRESHOLD);
        long totalCount = result.countByType().values().stream().mapToLong(Long::longValue).sum();
        assertThat(totalCount).isEqualTo(DATA_SIZE);
    }
}
