package br.edu.rotavital.service;

import br.edu.rotavital.model.BloodRequest;
import br.edu.rotavital.model.BloodType;
import br.edu.rotavital.model.DonorStock;
import br.edu.rotavital.service.SequentialProcessor.ProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Processador PARALELO de validação em lote de compatibilidade.
 *
 * Estratégia de paralelização:
 *   1. Divide os N registros em T fatias contíguas (sem sobreposição)
 *   2. Cria T Callable, um por fatia, usando SequentialProcessor.processSlice()
 *   3. Submete ao ExecutorService com T threads fixas
 *   4. Aguarda todos os futures (Future.get())
 *   5. Agrega os T resultados parciais em O(T) — desprezível vs O(N/T)
 *
 * Não há escrita em estrutura compartilhada durante o processamento:
 *   - O stockMap é somente-leitura (EnumMap imutável)
 *   - Cada thread opera exclusivamente na sua própria fatia da lista
 *   - A lista de BloodRequest é imutável (Collections.unmodifiableList)
 *   → Ausência total de race conditions
 *
 * Suporte a Virtual Threads do Java 21 (Project Loom):
 *   Quando useVirtualThreads=true, usa Executors.newVirtualThreadPerTaskExecutor()
 *   em vez de um pool de threads de plataforma fixo.
 */
@Service
public class ParallelProcessor {

    private static final Logger log = LoggerFactory.getLogger(ParallelProcessor.class);

    private final SequentialProcessor sequentialProcessor;

    public ParallelProcessor(SequentialProcessor sequentialProcessor) {
        this.sequentialProcessor = sequentialProcessor;
    }

    /**
     * Processa todas as requisições dividindo em T fatias processadas em paralelo.
     *
     * @param requests          Lista de requisições (imutável)
     * @param stockMap          Mapa de estoque (somente-leitura)
     * @param threshold         Limiar de criticidade
     * @param numThreads        Número de threads de plataforma a usar
     * @param useVirtualThreads Se true, ignora numThreads e usa Virtual Threads (Java 21)
     * @return Resultado agregado — idêntico ao do SequentialProcessor para os mesmos dados
     * @throws RuntimeException se qualquer thread falhar
     */
    public ProcessingResult processParallel(
            List<BloodRequest> requests,
            Map<BloodType, DonorStock> stockMap,
            double threshold,
            int numThreads,
            boolean useVirtualThreads) {

        int n = requests.size();
        int actualThreads = Math.min(numThreads, n); // não cria threads vazias

        log.debug("Iniciando processamento PARALELO: {} requisições, {} threads, virtualThreads={}",
                n, actualThreads, useVirtualThreads);

        // ── Criação do ExecutorService ────────────────────────────────────────
        ExecutorService executor = useVirtualThreads
                ? Executors.newVirtualThreadPerTaskExecutor()           // Java 21 Loom
                : Executors.newFixedThreadPool(actualThreads);          // Platform threads
        // ────────────────────────────────────────────────────────────────────

        try {
            List<Callable<ProcessingResult>> tasks = buildTasks(
                    requests, stockMap, threshold, n, actualThreads);

            // Submissão e espera
            List<Future<ProcessingResult>> futures = executor.invokeAll(tasks);

            // ── Passo de merge: O(T) onde T = numThreads ─────────────────────
            return mergeFutures(futures);
            // ────────────────────────────────────────────────────────────────

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Processamento paralelo interrompido", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Constrói a lista de Callable, um por fatia.
     *
     * Cada fatia é um intervalo [fromIndex, toIndex) da lista original.
     * As fatias são contíguas e cobrem toda a lista sem lacunas:
     *   Thread 0: [0, n/T)
     *   Thread 1: [n/T, 2n/T)
     *   ...
     *   Thread T-1: [(T-1)n/T, n)  ← absorve o resto da divisão inteira
     */
    private List<Callable<ProcessingResult>> buildTasks(
            List<BloodRequest> requests,
            Map<BloodType, DonorStock> stockMap,
            double threshold,
            int n,
            int numThreads) {

        List<Callable<ProcessingResult>> tasks = new ArrayList<>(numThreads);
        int sliceSize = n / numThreads;

        for (int t = 0; t < numThreads; t++) {
            int from = t * sliceSize;
            int to = (t == numThreads - 1) ? n : from + sliceSize; // última thread pega o resto

            final int finalFrom = from;
            final int finalTo = to;

            tasks.add(() -> {
                log.debug("Thread {} processando fatia [{}, {})", Thread.currentThread().getName(), finalFrom, finalTo);
                return sequentialProcessor.processSlice(requests, finalFrom, finalTo, stockMap, threshold);
            });
        }

        return tasks;
    }

    /**
     * Agrega os resultados parciais de todas as futures.
     * Lança RuntimeException se alguma thread falhou com exceção.
     */
    private ProcessingResult mergeFutures(List<Future<ProcessingResult>> futures)
            throws InterruptedException {

        ProcessingResult merged = ProcessingResult.empty();

        for (Future<ProcessingResult> future : futures) {
            try {
                merged = merged.merge(future.get());
            } catch (ExecutionException e) {
                throw new RuntimeException("Falha em thread de processamento: " + e.getCause().getMessage(), e);
            }
        }

        return merged;
    }
}
