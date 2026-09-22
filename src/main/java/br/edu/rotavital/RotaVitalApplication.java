package br.edu.rotavital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Rota Vital — Módulo de Paralelismo
 *
 * Projeto Integrador ADS - 3º Semestre
 * Disciplina: Algoritmos e Estruturas de Dados
 *
 * Demonstra a aceleração de validação em lote de compatibilidade sanguínea
 * usando processamento paralelo com ExecutorService e Virtual Threads (Java 21).
 */
@SpringBootApplication
public class RotaVitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RotaVitalApplication.class, args);
    }
}
