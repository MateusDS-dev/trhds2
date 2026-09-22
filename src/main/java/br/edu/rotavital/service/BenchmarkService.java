package br.edu.rotavital.service;

import br.edu.rotavital.dto.BenchmarkRequest;
import br.edu.rotavital.dto.BenchmarkResponse;
import br.edu.rotavital.model.BloodRequest;
import br.edu.rotavital.model.BloodType;
import br.edu.rotavital.model.DonorStock;
import br.edu.rotavital.service.SequentialProcessor.ProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orquestra a execução do benchmark e a formatação da resposta.
 *
 * Mantém o tempo do último processamento sequencial para calcular
 * o speedup relativo nas execuções paralelas.
 */
@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final DataGeneratorService dataGenerator;
    private final SequentialProcessor sequentialProcessor;
    private final ParallelProcessor parallelProcessor;

    // Armazena o tempo (ms) do último benchmark sequencial para cálculo de speedup
    private final AtomicLong lastSequentialMs = new AtomicLong(0L);

    public BenchmarkService(DataGeneratorService dataGenerator,
                            SequentialProcessor sequentialProcessor,
                            ParallelProcessor parallelProcessor) {
        this.dataGenerator = dataGenerator;
        this.sequentialProcessor = sequentialProcessor;
        this.parallelProcessor = parallelProcessor;
    }

    // ── Benchmark Sequencial ─────────────────────────────────────────────────

    /**
     * Executa o processamento sequencial e retorna a resposta formatada.
     * Armazena o tempo para cálculo posterior de speedup.
     */
    public BenchmarkResponse runSequential(BenchmarkRequest req) {
        List<BloodRequest> requests = dataGenerator.getOrGenerateRequests(req.dataSize());
        Map<BloodType, DonorStock> stock = dataGenerator.getStockMap();

        log.info("[SEQUENCIAL] Iniciando benchmark: dataSize={}", req.dataSize());

        long start = System.currentTimeMillis();
        ProcessingResult result = sequentialProcessor.processAll(requests, stock, req.criticalThreshold());
        long elapsed = System.currentTimeMillis() - start;

        lastSequentialMs.set(elapsed);
        log.info("[SEQUENCIAL] Concluído em {} ms", elapsed);

        return buildResponse("sequential", 1, req.dataSize(), elapsed, result, null);
    }

    // ── Benchmark Paralelo ───────────────────────────────────────────────────

    /**
     * Executa o processamento paralelo com T threads e retorna a resposta formatada.
     * Calcula o speedup em relação ao último benchmark sequencial registrado.
     */
    public BenchmarkResponse runParallel(BenchmarkRequest req) {
        List<BloodRequest> requests = dataGenerator.getOrGenerateRequests(req.dataSize());
        Map<BloodType, DonorStock> stock = dataGenerator.getStockMap();

        String version = req.useVirtualThreads() ? "parallel-virtual" : "parallel";
        log.info("[{}] Iniciando benchmark: dataSize={}, threads={}", version, req.dataSize(), req.threads());

        long start = System.currentTimeMillis();
        ProcessingResult result = parallelProcessor.processParallel(
                requests, stock, req.criticalThreshold(), req.threads(), req.useVirtualThreads());
        long elapsed = System.currentTimeMillis() - start;

        log.info("[{}] Concluído em {} ms", version, elapsed);

        // Speedup = T_sequencial / T_paralelo
        Double speedup = null;
        long seqMs = lastSequentialMs.get();
        if (seqMs > 0 && elapsed > 0) {
            speedup = Math.round((double) seqMs / elapsed * 100.0) / 100.0;
        }

        return buildResponse(version, req.threads(), req.dataSize(), elapsed, result, speedup);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private BenchmarkResponse buildResponse(String version, int threads, int dataSize,
                                             long elapsedMs, ProcessingResult result,
                                             Double speedup) {
        Map<String, Double> coverageByBloodType = computeAverageCoverage(result);

        return new BenchmarkResponse(
                version,
                threads,
                dataSize,
                elapsedMs,
                result.criticalCount(),
                Math.round(result.totalCoverageScore() * 1000.0) / 1000.0,
                coverageByBloodType,
                speedup
        );
    }

    /**
     * Calcula a cobertura média por tipo sanguíneo a partir dos acumuladores.
     * Resultado ordenado por tipo para facilitar comparação entre versões.
     */
    private Map<String, Double> computeAverageCoverage(ProcessingResult result) {
        Map<String, Double> avg = new LinkedHashMap<>();
        for (BloodType bt : BloodType.values()) {
            long count = result.countByType().getOrDefault(bt, 0L);
            double sum = result.coverageSumByType().getOrDefault(bt, 0.0);
            avg.put(bt.getLabel(), count > 0
                    ? Math.round(sum / count * 10000.0) / 10000.0
                    : 0.0);
        }
        return avg;
    }
}
