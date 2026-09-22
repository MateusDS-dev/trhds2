package br.edu.rotavital.service;

import br.edu.rotavital.model.BloodRequest;
import br.edu.rotavital.model.BloodType;
import br.edu.rotavital.model.DonorStock;

import java.util.Map;

/**
 * Lógica central de compatibilidade sanguínea.
 *
 * Para cada requisição, calcula:
 *   - unidades compatíveis disponíveis no estoque nacional
 *   - percentual de cobertura em relação ao solicitado
 *   - se a cobertura está abaixo do threshold crítico
 *
 * Esta é a operação O(1) por requisição que, aplicada a N requisições,
 * produz complexidade O(N) total — o gargalo que paralelizamos.
 *
 * Não possui estado: pode ser chamada de múltiplas threads com segurança.
 */
public class CompatibilityService {

    private CompatibilityService() {} // utilitária estática

    /**
     * Calcula a cobertura de estoque para uma requisição de sangue.
     *
     * Algoritmo:
     *   1. Obtém o tipo requerido pela requisição
     *   2. Consulta a tabela de compatibilidade (EnumSet.contains = O(1))
     *   3. Soma as bolsas disponíveis de cada tipo compatível no estoque
     *   4. Calcula cobertura = disponível / solicitado (capped em 1.0)
     *
     * @param request  Requisição de sangue do hospital
     * @param stockMap Mapa de estoque (somente-leitura, seguro para acesso concorrente)
     * @return Cobertura percentual [0.0, 1.0]
     */
    public static double computeCoverage(BloodRequest request,
                                         Map<BloodType, DonorStock> stockMap) {
        long unitsNeeded = request.unitsNeeded();
        long unitsAvailable = 0L;

        // Itera pelos 8 tipos sanguíneos (loop fixo = O(1) por requisição)
        for (BloodType donorType : BloodType.values()) {
            if (request.requiredType().accepts(donorType)) {
                DonorStock stock = stockMap.get(donorType);
                if (stock != null) {
                    unitsAvailable += stock.unitsAvailable();
                }
            }
        }

        if (unitsNeeded <= 0) return 1.0;

        // Cobertura normalizada entre 0 e 1
        double coverage = (double) unitsAvailable / unitsNeeded;
        return Math.min(coverage, 1.0);
    }

    /**
     * Determina se uma requisição está em estado crítico de falta de estoque.
     *
     * @param coverage  Cobertura calculada por computeCoverage()
     * @param threshold Limiar abaixo do qual é crítico (ex: 0.20 = 20%)
     * @param urgency   Urgência da requisição (CRITICAL sempre conta como crítica se cobertura < threshold)
     * @return true se a situação for crítica
     */
    public static boolean isCritical(double coverage, double threshold,
                                      BloodRequest.Urgency urgency) {
        boolean belowThreshold = coverage < threshold;
        return belowThreshold && urgency != BloodRequest.Urgency.NORMAL;
    }
}
