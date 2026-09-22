# Rota Vital — Módulo de Paralelismo

> **Projeto Integrador ADS · 3º Semestre**  
> **Disciplina:** Algoritmos e Estruturas de Dados  
> **Tema:** Paralelismo na Camada de Aplicação — Validação em Lote de Compatibilidade Sanguínea

---

## Visão Geral

Este módulo demonstra a aceleração de uma operação CPU-bound usando **processamento paralelo com `ExecutorService`** e **Virtual Threads do Java 21 (Project Loom)**.

**Operação escolhida:** Validação em lote de compatibilidade sanguínea (ABO+Rh) entre requisições de hospitais e estoque nacional de doadores.

**Stack:** Java 21 + Spring Boot 3.2

---

## Estrutura do Projeto

```
rota-vital-paralelo/
├── pom.xml
├── docs/
│   ├── justificativa.md        # Texto para PDF (~2 páginas)
│   └── analise_final.md        # Tabela de medições + análise + comandos curl
└── src/
    ├── main/java/br/edu/rotavital/
    │   ├── RotaVitalApplication.java
    │   ├── model/
    │   │   ├── BloodType.java          # Enum com tabela de compatibilidade
    │   │   ├── BloodRequest.java       # Requisição de hospital
    │   │   └── DonorStock.java         # Estoque por tipo sanguíneo
    │   ├── dto/
    │   │   ├── BenchmarkRequest.java
    │   │   └── BenchmarkResponse.java
    │   ├── service/
    │   │   ├── DataGeneratorService.java   # Geração de massa sintética
    │   │   ├── CompatibilityService.java   # Lógica de compatibilidade (stateless)
    │   │   ├── SequentialProcessor.java    # Versão single-thread + processSlice()
    │   │   ├── ParallelProcessor.java      # Versão multi-thread + Virtual Threads
    │   │   └── BenchmarkService.java       # Orquestração + cálculo de speedup
    │   └── controller/
    │       └── BenchmarkController.java    # Endpoints REST
    ├── main/resources/
    │   └── application.properties
    └── test/java/br/edu/rotavital/
        └── RotaVitalBenchmarkTest.java     # Testes de correção (sem race condition)
```

---

## Pré-requisitos

- Java 21+
- Maven 3.8+

```bash
java -version  # deve mostrar 21.x
mvn -version
```

---

## Como Executar

### 1. Clonar e subir o servidor

```bash
git clone <url-do-repositorio>
cd rota-vital-paralelo
mvn spring-boot:run
```

O servidor sobe em `http://localhost:8080`.

### 2. Verificar o ambiente

```bash
curl http://localhost:8080/benchmark/info
```

### 3. Pré-aquecer o cache de dados

```bash
# Gera e cacheia 1 milhão de registros (faz uma vez antes dos benchmarks)
curl "http://localhost:8080/benchmark/warm?n=1000000"
```

### 4. Executar os benchmarks

```bash
# Sequencial (baseline)
curl -s -X POST http://localhost:8080/benchmark/sequential \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":1,"criticalThreshold":0.20,"useVirtualThreads":false}' \
     | python3 -m json.tool

# Paralelo — 2 threads
curl -s -X POST http://localhost:8080/benchmark/parallel \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":2,"criticalThreshold":0.20,"useVirtualThreads":false}' \
     | python3 -m json.tool

# Paralelo — 4 threads
curl -s -X POST http://localhost:8080/benchmark/parallel \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":4,"criticalThreshold":0.20,"useVirtualThreads":false}' \
     | python3 -m json.tool

# Paralelo — 8 threads
curl -s -X POST http://localhost:8080/benchmark/parallel \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":8,"criticalThreshold":0.20,"useVirtualThreads":false}' \
     | python3 -m json.tool

# Virtual Threads Java 21 (extra)
curl -s -X POST http://localhost:8080/benchmark/parallel \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":8,"criticalThreshold":0.20,"useVirtualThreads":true}' \
     | python3 -m json.tool
```

### 5. Executar os testes automatizados

```bash
mvn test
```

Os testes verificam que o resultado paralelo (1, 2, 4 e 8 threads) é **idêntico** ao sequencial — prova formal de ausência de race conditions.

---

## Endpoints

| Método | Path | Descrição |
|--------|------|-----------|
| `POST` | `/benchmark/sequential` | Processamento single-thread |
| `POST` | `/benchmark/parallel` | Processamento multi-thread |
| `GET` | `/benchmark/warm?n=N` | Pré-aquece cache de N registros |
| `GET` | `/benchmark/info` | Info do ambiente (núcleos, JVM) |
| `GET` | `/actuator/health` | Health check |

### Payload de Entrada (`BenchmarkRequest`)

```json
{
  "dataSize": 1000000,
  "threads": 4,
  "criticalThreshold": 0.20,
  "useVirtualThreads": false
}
```

| Campo | Descrição | Padrão |
|-------|-----------|--------|
| `dataSize` | Número de requisições a processar | 100.000 |
| `threads` | Threads paralelas (ignorado em `/sequential`) | 4 |
| `criticalThreshold` | % mínimo de cobertura para não ser crítico | 0.20 (20%) |
| `useVirtualThreads` | Usa Virtual Threads Java 21 (Project Loom) | false |

### Payload de Saída (`BenchmarkResponse`)

```json
{
  "version": "parallel",
  "threads": 4,
  "dataSize": 1000000,
  "elapsedMs": 312,
  "criticalRequests": 14823,
  "totalCompatibilityScore": 892341.5,
  "coverageByBloodType": {
    "O+": 1.0,
    "O-": 1.0,
    "A+": 1.0,
    "A-": 1.0,
    "B+": 1.0,
    "B-": 1.0,
    "AB+": 0.87,
    "AB-": 0.91
  },
  "speedupVsSequential": 3.7
}
```

---

## Como Verificar Ausência de Race Condition

Execute sequencial e paralelo com o **mesmo `dataSize` e `criticalThreshold`** e compare:

```
criticalRequests       → deve ser IDÊNTICO
totalCompatibilityScore → deve diferir < 0.001 (arredondamento de double)
coverageByBloodType    → cada tipo deve diferir < 0.001
```

Se divergir: há race condition. Execute `mvn test` para diagnóstico.

---

## Complexidade

| Versão | Complexidade | Observação |
|--------|-------------|------------|
| Sequencial | O(N) | K=8 tipos é constante |
| Paralela | O(N/T) + O(T) ≈ O(N/T) | Merge desprezível |
| Speedup teórico | T | Lei de Amdahl: prático < T |

---

## Documentação

- [`docs/justificativa.md`](docs/justificativa.md) — Justificativa técnica (~2 páginas) para exportar em PDF
- [`docs/analise_final.md`](docs/analise_final.md) — Tabela de medições + análise final de 15–20 linhas

---

## Tecnologias

- **Java 21** (Virtual Threads / Project Loom)
- **Spring Boot 3.2**
- **JUnit 5** + AssertJ
- **Maven**
