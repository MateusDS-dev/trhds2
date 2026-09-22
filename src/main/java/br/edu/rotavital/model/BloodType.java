package br.edu.rotavital.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Enum representando os 8 tipos sanguíneos do sistema ABO+Rh.
 *
 * Encapsula a tabela de compatibilidade transfusional:
 *   - compatibleDonors: quais tipos podem DOAR para este tipo
 *   - compatibleRecipients: quais tipos este tipo pode RECEBER
 *
 * A tabela é pré-computada (O(1) de acesso) — esse é o ponto central
 * do argumento de gargalo-em-CPU: com a tabela em memória, cada
 * validação é apenas uma operação de EnumSet.contains(), sem I/O.
 */
public enum BloodType {

    O_NEGATIVE("O-"),
    O_POSITIVE("O+"),
    A_NEGATIVE("A-"),
    A_POSITIVE("A+"),
    B_NEGATIVE("B-"),
    B_POSITIVE("B+"),
    AB_NEGATIVE("AB-"),
    AB_POSITIVE("AB+");

    private final String label;

    // Conjunto de tipos que PODEM ser usados para abastecer uma requisição deste tipo
    // (i.e., tipos compatíveis para doação a este receptor)
    private Set<BloodType> compatibleDonors;

    BloodType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Retorna os tipos sanguíneos que podem DOAR para este receptor.
     * Inicializado via bloco estático para evitar dependência circular no enum.
     */
    public Set<BloodType> getCompatibleDonors() {
        return compatibleDonors;
    }

    /**
     * Verifica se um doador de tipo 'donorType' é compatível com este receptor.
     * Complexidade: O(1) — EnumSet usa bitmask interno.
     */
    public boolean accepts(BloodType donorType) {
        return compatibleDonors.contains(donorType);
    }

    /**
     * Tabela de compatibilidade transfusional ABO+Rh completa.
     * Inicializada estaticamente após todas as constantes do enum serem criadas.
     *
     * Regras:
     *   O- → doa para todos
     *   O+ → doa para O+, A+, B+, AB+
     *   A- → doa para A-, A+, AB-, AB+
     *   A+ → doa para A+, AB+
     *   B- → doa para B-, B+, AB-, AB+
     *   B+ → doa para B+, AB+
     *   AB- → doa para AB-, AB+
     *   AB+ → doa somente para AB+
     */
    static {
        O_NEGATIVE.compatibleDonors = EnumSet.of(O_NEGATIVE);
        O_POSITIVE.compatibleDonors = EnumSet.of(O_NEGATIVE, O_POSITIVE);
        A_NEGATIVE.compatibleDonors = EnumSet.of(O_NEGATIVE, A_NEGATIVE);
        A_POSITIVE.compatibleDonors = EnumSet.of(O_NEGATIVE, O_POSITIVE, A_NEGATIVE, A_POSITIVE);
        B_NEGATIVE.compatibleDonors = EnumSet.of(O_NEGATIVE, B_NEGATIVE);
        B_POSITIVE.compatibleDonors = EnumSet.of(O_NEGATIVE, O_POSITIVE, B_NEGATIVE, B_POSITIVE);
        AB_NEGATIVE.compatibleDonors = EnumSet.of(O_NEGATIVE, A_NEGATIVE, B_NEGATIVE, AB_NEGATIVE);
        AB_POSITIVE.compatibleDonors = EnumSet.copyOf(EnumSet.allOf(BloodType.class)); // recebe de todos
    }
}
