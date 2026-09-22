package br.edu.rotavital.model;

/**
 * Representa uma requisição de sangue feita por um hospital.
 *
 * Campos relevantes para o benchmark de compatibilidade:
 *   - id: identificador único da requisição
 *   - hospitalId: hospital solicitante
 *   - requiredType: tipo sanguíneo necessário (receptor)
 *   - urgency: nível de urgência (impacta threshold de cobertura crítica)
 *   - unitsNeeded: quantidade de bolsas solicitadas
 */
public record BloodRequest(
        long id,
        String hospitalId,
        BloodType requiredType,
        Urgency urgency,
        int unitsNeeded
) {
    public enum Urgency {
        CRITICAL,   // Risco de vida imediato — cobertura < 20% é alarme vermelho
        HIGH,       // Cirurgia agendada em < 24h
        NORMAL      // Reposição de estoque rotineira
    }
}
