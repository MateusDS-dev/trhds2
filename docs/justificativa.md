# Justificativa Técnica — Módulo de Paralelismo
### Rota Vital · Projeto Integrador ADS · 3º Semestre
### Disciplina: Algoritmos e Estruturas de Dados

---

## 1. Identificação da Operação

A operação escolhida para paralelização é a **validação em lote de compatibilidade sanguínea**. Em escala nacional, o Rota Vital recebe simultaneamente milhares de requisições de hospitais cadastrados, cada uma especificando um tipo sanguíneo necessário e a quantidade de bolsas solicitadas. A camada de aplicação precisa, para cada requisição, verificar quais tipos sanguíneos disponíveis no estoque central são transfusionalmente compatíveis com o receptor e calcular o percentual de cobertura (bolsas disponíveis ÷ bolsas solicitadas). Com um volume de 1 milhão de requisições por ciclo de processamento, essa operação passa a ser o principal gargalo da camada de aplicação.

---

## 2. Onde Está o Gargalo Computacional

É fundamental distinguir duas categorias de tempo de resposta em sistemas distribuídos:

**Gargalo de I/O** (banco de dados, rede): o processador fica ocioso aguardando dados. Escalar com threads *de plataforma* ajuda pouco; a solução correta é I/O assíncrono ou connection pooling.

**Gargalo de CPU** (processamento, cálculo): o processador está 100% ocupado. Escalar com múltiplas threads — uma por núcleo — divide o trabalho e reduz o tempo de parede (*wall-clock time*).

A validação de compatibilidade, **após os dados serem carregados do banco**, é puro processamento:

1. A tabela de compatibilidade ABO+Rh é um `EnumSet` pré-computado e mantido em memória RAM.
2. Para cada requisição, o algoritmo realiza exatamente **8 comparações** (uma por tipo sanguíneo) via `EnumSet.contains()`, operação com complexidade O(1) por usar bitmask.
3. Não há acesso a disco, consulta SQL, chamada de rede ou sincronização de estado durante o loop principal.

Portanto, **o tempo total é determinado pelo número de requisições multiplicado pelo custo por requisição — processamento puro de CPU**.

---

## 3. Análise de Complexidade (Big-O)

### Versão Sequencial

Seja N o número de requisições e K = 8 (número de tipos sanguíneos, constante):

```
T_seq = O(N × K) = O(N)     # K é constante, absorvido pela notação Big-O
```

Para N = 1.000.000, o algoritmo executa aproximadamente 8 milhões de comparações em memória. Em hardware moderno (~10⁹ operações simples/segundo), o tempo esperado é da ordem de **dezenas a centenas de milissegundos** dependendo da JVM e do hardware — mensurável, e crescente linearmente com N.

### Versão Paralela

Com T threads, cada thread processa uma fatia de tamanho N/T:

```
T_par = O(N/T) + O(T)    # processamento paralelo + merge final
     ≈ O(N/T)             # merge é desprezível: K×T << N
```

O speedup teórico (Lei de Amdahl simplificada, assumindo 100% paralelizável):

```
Speedup = T_seq / T_par ≈ T
```

Na prática, o speedup real é menor que T por conta de:
- **Overhead de criação e gestão de threads** (fixo por execução, não por requisição)
- **Parte sequencial não paralelizável**: a geração de dados e o passo de merge
- **Contenção de cache de CPU** (cache coherency entre núcleos)
- **Saturação de núcleos**: com T > núcleos físicos, não há ganho real

---

## 4. Particionamento dos Dados

Os dados podem ser divididos em fatias independentes porque **cada requisição é processada de forma autônoma**:

- A cobertura de uma requisição não depende do resultado de nenhuma outra requisição.
- O mapa de estoque (`stockMap`) é **somente-leitura** durante todo o processamento — qualquer número de threads pode lê-lo simultaneamente sem race condition.
- A lista de requisições é imutável após a geração (`Collections.unmodifiableList`).
- Cada thread escreve apenas em sua **própria estrutura local** (`ProcessingResult` privado), nunca em memória compartilhada com outras threads.

O passo de **merge** final — somar os resultados parciais — é O(T × K) = O(T), onde T é o número de threads e K = 8. Para T ≤ 8 e N = 1.000.000, o merge representa menos de **0,006%** do trabalho total, sendo completamente desprezível na análise de desempenho.

**Conclusão:** a operação satisfaz todos os critérios para paralelização eficiente — gargalo em CPU, partição disjunta, ausência de estado compartilhado mutável e passo de merge de custo negligenciável.
