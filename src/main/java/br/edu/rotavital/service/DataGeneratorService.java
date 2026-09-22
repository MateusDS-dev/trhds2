package br.edu.rotavital.service;

import br.edu.rotavital.model.BloodRequest;
import br.edu.rotavital.model.BloodRequest.Urgency;
import br.edu.rotavital.model.BloodType;
import br.edu.rotavital.model.DonorStock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço responsável pela geração de massa de dados sintética.
 *
 * Simula o cenário de escala nacional do Rota Vital:
 *   - N requisições de hospitais distribuídas por tipo sanguíneo e urgência
 *   - Estoque de doadores com distribuição realista da população brasileira
 *
 * Os dados são cacheados em memória após a primeira geração para cada tamanho,
 * garantindo que os benchmarks sequencial e paralelo operem sobre os mesmos dados.
 *
 * Distribuição de tipos sanguíneos no Brasil (aproximada):
 *   O+ 36%  | O- 7%
 *   A+ 34%  | A- 6%
 *   B+  8%  | B- 2%
 *   AB+ 6%  | AB- 1%
 */
@Service
public class DataGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(DataGeneratorService.class);

    // Cache: tamanho → lista de requisições
    private final Map<Integer, List<BloodRequest>> requestCache = new ConcurrentHashMap<>();

    // Estoque nacional simulado (fixo para todos os benchmarks)
    private final Map<BloodType, DonorStock> stockMap = new EnumMap<>(BloodType.class);

    // Distribuição de tipos sanguíneos na população brasileira (em %)
    private static final double[] BLOOD_TYPE_DIST = {
            36.0, // O+
             7.0, // O-
            34.0, // A+
             6.0, // A-
             8.0, // B+
             2.0, // B-
             6.0, // AB+
             1.0  // AB-
    };

    private static final BloodType[] BLOOD_TYPE_ORDER = {
            BloodType.O_POSITIVE, BloodType.O_NEGATIVE,
            BloodType.A_POSITIVE, BloodType.A_NEGATIVE,
            BloodType.B_POSITIVE, BloodType.B_NEGATIVE,
            BloodType.AB_POSITIVE, BloodType.AB_NEGATIVE
    };

    // Sufixos de hospitais para simular ~5000 hospitais nacionais
    private static final int TOTAL_HOSPITALS = 5_000;

    public DataGeneratorService() {
        initializeStock();
    }

    /**
     * Inicializa o estoque nacional com valores realistas.
     * Distribuição proporcional à frequência do tipo sanguíneo na população.
     */
    private void initializeStock() {
        // Estoque base: ~500.000 bolsas no total no sistema nacional
        long totalUnits = 500_000L;
        for (int i = 0; i < BLOOD_TYPE_ORDER.length; i++) {
            long units = Math.round(totalUnits * BLOOD_TYPE_DIST[i] / 100.0);
            stockMap.put(BLOOD_TYPE_ORDER[i], new DonorStock(BLOOD_TYPE_ORDER[i], units));
        }
        log.info("Estoque nacional inicializado: {} entradas, {} bolsas totais",
                stockMap.size(), stockMap.values().stream().mapToLong(DonorStock::unitsAvailable).sum());
    }

    /**
     * Retorna (ou gera e cacheia) uma lista de N requisições sintéticas.
     *
     * A distribuição de urgência é:
     *   CRITICAL: 10%
     *   HIGH:     30%
     *   NORMAL:   60%
     *
     * @param n Número de requisições a gerar
     * @return Lista imutável de BloodRequest
     */
    public List<BloodRequest> getOrGenerateRequests(int n) {
        return requestCache.computeIfAbsent(n, this::generateRequests);
    }

    private List<BloodRequest> generateRequests(int n) {
        log.info("Gerando {} requisições sintéticas...", n);
        long start = System.currentTimeMillis();

        Random rng = new Random(42L); // seed fixo para reprodutibilidade
        List<BloodRequest> requests = new ArrayList<>(n);

        // Pré-calcula thresholds acumulados para sorteio por distribuição
        double[] cumulative = buildCumulativeDistribution(BLOOD_TYPE_DIST);

        for (int i = 0; i < n; i++) {
            BloodType type = sampleBloodType(rng.nextDouble(), cumulative);
            Urgency urgency = sampleUrgency(rng.nextDouble());
            int units = 1 + rng.nextInt(10); // 1 a 10 bolsas por requisição
            String hospitalId = "HOSP-" + String.format("%05d", rng.nextInt(TOTAL_HOSPITALS) + 1);

            requests.add(new BloodRequest(i + 1L, hospitalId, type, urgency, units));
        }

        log.info("Geração concluída em {} ms", System.currentTimeMillis() - start);
        return Collections.unmodifiableList(requests);
    }

    /**
     * Retorna o mapa de estoque (somente-leitura).
     * Compartilhado entre todas as threads sem necessidade de sincronização,
     * pois nunca é modificado após a inicialização.
     */
    public Map<BloodType, DonorStock> getStockMap() {
        return Collections.unmodifiableMap(stockMap);
    }

    // ── Utilitários de amostragem ────────────────────────────────────────────

    private double[] buildCumulativeDistribution(double[] dist) {
        double[] cumulative = new double[dist.length];
        double sum = 0;
        for (int i = 0; i < dist.length; i++) {
            sum += dist[i];
            cumulative[i] = sum;
        }
        return cumulative;
    }

    private BloodType sampleBloodType(double rand, double[] cumulative) {
        double scaled = rand * 100.0;
        for (int i = 0; i < cumulative.length; i++) {
            if (scaled <= cumulative[i]) return BLOOD_TYPE_ORDER[i];
        }
        return BLOOD_TYPE_ORDER[BLOOD_TYPE_ORDER.length - 1];
    }

    private Urgency sampleUrgency(double rand) {
        if (rand < 0.10) return Urgency.CRITICAL;
        if (rand < 0.40) return Urgency.HIGH;
        return Urgency.NORMAL;
    }
}
