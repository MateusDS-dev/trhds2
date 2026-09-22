package br.edu.rotavital.controller;

import br.edu.rotavital.dto.BenchmarkRequest;
import br.edu.rotavital.dto.BenchmarkResponse;
import br.edu.rotavital.service.BenchmarkService;
import br.edu.rotavital.service.DataGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller REST que expõe os endpoints de benchmark do módulo de paralelismo.
 *
 * Endpoints disponíveis:
 *   POST /benchmark/sequential   → Processa N requisições em single-thread
 *   POST /benchmark/parallel     → Processa N requisições com T threads
 *   GET  /benchmark/warm?n=N     → Pré-aquece o cache de dados (útil antes dos testes)
 *   GET  /benchmark/info         → Retorna informações do ambiente (núcleos disponíveis, etc.)
 *
 * Para testar com curl:
 *   curl -X POST http://localhost:8080/benchmark/sequential \
 *        -H "Content-Type: application/json" \
 *        -d '{"dataSize": 100000, "threads": 1, "criticalThreshold": 0.20, "useVirtualThreads": false}'
 *
 *   curl -X POST http://localhost:8080/benchmark/parallel \
 *        -H "Content-Type: application/json" \
 *        -d '{"dataSize": 100000, "threads": 4, "criticalThreshold": 0.20, "useVirtualThreads": false}'
 */
@RestController
@RequestMapping("/benchmark")
@CrossOrigin(origins = "*") // Permite chamadas do front-end de demonstração
public class BenchmarkController {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkController.class);

    private final BenchmarkService benchmarkService;
    private final DataGeneratorService dataGeneratorService;

    public BenchmarkController(BenchmarkService benchmarkService,
                               DataGeneratorService dataGeneratorService) {
        this.benchmarkService = benchmarkService;
        this.dataGeneratorService = dataGeneratorService;
    }

    // ── POST /benchmark/sequential ───────────────────────────────────────────

    /**
     * Executa a validação em lote em modo SEQUENCIAL (single-thread).
     *
     * Serve como baseline para cálculo de speedup.
     * Execute este endpoint ANTES do paralelo para que o speedup seja calculado.
     */
    @PostMapping("/sequential")
    public ResponseEntity<BenchmarkResponse> runSequential(
            @RequestBody(required = false) BenchmarkRequest request) {

        BenchmarkRequest req = request != null ? request
                : new BenchmarkRequest(100_000, 1, 0.20, false);

        log.info("Requisição sequencial recebida: dataSize={}", req.dataSize());
        BenchmarkResponse response = benchmarkService.runSequential(req);
        return ResponseEntity.ok(response);
    }

    // ── POST /benchmark/parallel ─────────────────────────────────────────────

    /**
     * Executa a validação em lote em modo PARALELO com T threads.
     *
     * Para comparação correta, use o mesmo dataSize e criticalThreshold
     * do endpoint sequencial — os resultados devem ser IDÊNTICOS.
     * Se diferirem, indica race condition.
     *
     * Variações testadas:
     *   threads: 2, 4, 8
     *   dataSize: 100000, 500000, 1000000
     *   useVirtualThreads: false (platform), true (virtual/Loom)
     */
    @PostMapping("/parallel")
    public ResponseEntity<BenchmarkResponse> runParallel(
            @RequestBody(required = false) BenchmarkRequest request) {

        BenchmarkRequest req = request != null ? request
                : new BenchmarkRequest(100_000, 4, 0.20, false);

        log.info("Requisição paralela recebida: dataSize={}, threads={}, virtual={}",
                req.dataSize(), req.threads(), req.useVirtualThreads());
        BenchmarkResponse response = benchmarkService.runParallel(req);
        return ResponseEntity.ok(response);
    }

    // ── GET /benchmark/warm ──────────────────────────────────────────────────

    /**
     * Pré-aquece o cache de dados para um dado tamanho.
     * Execute antes de iniciar os benchmarks para eliminar o tempo de geração
     * de dados dos tempos medidos.
     */
    @GetMapping("/warm")
    public ResponseEntity<Map<String, Object>> warmup(@RequestParam(defaultValue = "100000") int n) {
        log.info("Aquecendo cache para n={}", n);
        long start = System.currentTimeMillis();
        int size = dataGeneratorService.getOrGenerateRequests(n).size();
        long elapsed = System.currentTimeMillis() - start;

        return ResponseEntity.ok(Map.of(
                "status", "ready",
                "dataSize", size,
                "generationMs", elapsed,
                "message", "Dados prontos. Execute os benchmarks agora."
        ));
    }

    // ── GET /benchmark/info ──────────────────────────────────────────────────

    /**
     * Retorna informações do ambiente de execução.
     * Útil para contextualizar os resultados do benchmark.
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Runtime rt = Runtime.getRuntime();
        return ResponseEntity.ok(Map.of(
                "javaVersion", System.getProperty("java.version"),
                "availableProcessors", rt.availableProcessors(),
                "maxMemoryMB", rt.maxMemory() / (1024 * 1024),
                "os", System.getProperty("os.name") + " " + System.getProperty("os.arch"),
                "virtualThreadsSupported", true, // Java 21+
                "hint", "availableProcessors indica o número ideal de threads para CPU-bound tasks"
        ));
    }
}
