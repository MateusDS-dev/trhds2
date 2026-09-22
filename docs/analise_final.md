# Análise de Desempenho — Paralelismo no Rota Vital
### Rota Vital · Projeto Integrador ADS · 3º Semestre

---

## Tabela de Resultados (preencha com suas medições)

Execute os endpoints nesta ordem para cada linha:
1. `GET /benchmark/warm?n=<dataSize>` (aquece o cache)
2. `POST /benchmark/sequential` com `dataSize`
3. `POST /benchmark/parallel` com `dataSize` e `threads` variando

> Ambiente: Java 21.0.12.1 · Mac OS X x86_64 · 12 núcleos disponíveis · 4096 MB RAM

| dataSize  | threads | Versão           | Tempo (ms) | Speedup |
|-----------|---------|------------------|------------|---------|
| 100.000   | 1 (seq) | sequential       | 45         | 1,00    |
| 100.000   | 2       | parallel         | 18         | 2,50    |
| 100.000   | 4       | parallel         | 13         | 3,46    |
| 100.000   | 8       | parallel         | 11         | 4,09    |
| 500.000   | 1 (seq) | sequential       | 186        | 1,00    |
| 500.000   | 2       | parallel         | 103        | 1,81    |
| 500.000   | 4       | parallel         | 44         | 4,23    |
| 500.000   | 8       | parallel         | 34         | 5,47    |
| 1.000.000 | 1 (seq) | sequential       | 320        | 1,00    |
| 1.000.000 | 2       | parallel         | 173        | 1,85    |
| 1.000.000 | 4       | parallel         | 95         | 3,37    |
| 1.000.000 | 8       | parallel         | 65         | 4,92    |
| 1.000.000 | 8       | parallel-virtual | 68         | 4,71    |

---

## Gráfico de Speedup (exemplo — substitua pelos seus dados reais)

```
Speedup
  4,0 |                              *
      |                         *
  3,0 |                    *
      |               *
  2,0 |          *
      |     *
  1,0 |*
      +--+--+--+--+--+--+--+--+-- threads
         1  2  3  4  5  6  7  8

  * Speedup ideal (linear):  Speedup = T
  * Speedup real (medido):   < T por overhead e lei de Amdahl
```

> Instrução: após preencher a tabela, crie o gráfico com os valores reais.
> Ferramenta sugerida: Google Sheets, Excel, ou Python (matplotlib).

---

## Análise Final (15–20 linhas)

O ganho de desempenho observado **não foi linear** em relação ao número de threads, e esse comportamento era esperado. A **Lei de Amdahl** estabelece que o speedup máximo de um programa é limitado pela fração que permanece sequencial: mesmo que 99% do código seja paralelizável, o 1% restante — criação do ExecutorService, divisão das fatias e o passo de merge — impõe um teto ao ganho.

Além disso, o **overhead de criação de threads de plataforma** tem custo fixo por execução (alocação de pilha, troca de contexto pelo SO), que se torna proporcionalmente grande quando o volume de dados é pequeno (100 mil registros) e desprezível para volumes grandes (1 milhão). Isso explica por que o speedup tende a ser mais próximo do ideal para entradas maiores.

A **Big-O do algoritmo não muda** com o paralelismo: a complexidade continua O(N) — simplesmente porque dividir N operações entre T threads não reduz o trabalho total, apenas o distribui. Formalmente, T × O(N/T) = O(N). O paralelismo reduz o **tempo de parede** (*wall-clock time*), não a complexidade.

É importante distinguir **concorrência de paralelismo**: em um sistema de atendimento de eventos simultâneos (como um servidor web gerenciando múltiplas conexões), threads atendem *eventos diferentes ao mesmo tempo* — isso é concorrência. Aqui, ao contrário, as threads **aceleram o processamento de um único volume de dados** dividindo-o em fatias — isso é paralelismo de dados. O uso é o inverso: lá, muitas threads para não bloquear; aqui, poucas threads bem dimensionadas (idealmente igual ao número de núcleos físicos) para maximizar CPU.

Quando nem 8 threads forem suficientes — seja por volume de dados crescendo além da capacidade de uma JVM, seja por requisitos de latência mais rígidos — a arquitetura pode evoluir em direção ao **processamento distribuído**: introduzir uma fila de mensagens (Apache Kafka, RabbitMQ) para distribuir as requisições entre múltiplas instâncias do Rota Vital em servidores distintos, implementar um framework de map-reduce (Apache Spark) para processar volumes de nível Big Data com tolerância a falhas, ou adotar uma arquitetura de microsserviços onde o serviço de compatibilidade escala horizontalmente de forma independente.

---

## Comandos curl para Reprodução

```bash
# 1. Informações do ambiente
curl http://localhost:8080/benchmark/info

# 2. Pré-aquecer cache (elimina tempo de geração dos testes)
curl "http://localhost:8080/benchmark/warm?n=1000000"

# 3. Sequencial
curl -s -X POST http://localhost:8080/benchmark/sequential \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":1,"criticalThreshold":0.20,"useVirtualThreads":false}' \
     | python3 -m json.tool

# 4. Paralelo com 2 threads
curl -s -X POST http://localhost:8080/benchmark/parallel \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":2,"criticalThreshold":0.20,"useVirtualThreads":false}' \
     | python3 -m json.tool

# 5. Paralelo com 4 threads
curl -s -X POST http://localhost:8080/benchmark/parallel \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":4,"criticalThreshold":0.20,"useVirtualThreads":false}' \
     | python3 -m json.tool

# 6. Paralelo com 8 threads
curl -s -X POST http://localhost:8080/benchmark/parallel \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":8,"criticalThreshold":0.20,"useVirtualThreads":false}' \
     | python3 -m json.tool

# 7. Extra: Virtual Threads Java 21
curl -s -X POST http://localhost:8080/benchmark/parallel \
     -H "Content-Type: application/json" \
     -d '{"dataSize":1000000,"threads":8,"criticalThreshold":0.20,"useVirtualThreads":true}' \
     | python3 -m json.tool
```

---

## Verificação de Ausência de Race Condition

Compare os campos `criticalRequests` e `totalCompatibilityScore` entre a resposta sequencial e as paralelas com o mesmo `dataSize` e `criticalThreshold`. Os valores devem ser **idênticos** (ou com diferença < 0,001 por arredondamento de ponto flutuante). Se divergirem, há race condition — execute os testes automatizados (`mvn test`) para identificar o problema.
