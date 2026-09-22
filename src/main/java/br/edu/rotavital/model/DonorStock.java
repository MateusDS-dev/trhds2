package br.edu.rotavital.model;

/**
 * Representa o estoque disponível de um tipo sanguíneo no banco de sangue.
 *
 * Em escala nacional, o DonorStock é agregado de todos os centros de coleta
 * do país — portanto, unitsAvailable pode ser um número grande.
 *
 * Para o benchmark, é lido como somente-leitura por todas as threads,
 * o que elimina race conditions sem necessidade de sincronização.
 */
public record DonorStock(
        BloodType bloodType,
        long unitsAvailable
) {}
