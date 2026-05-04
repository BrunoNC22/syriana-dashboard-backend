# 🚀 ROADMAP DE ANÁLISE (PASSO A PASSO)

## 🟢 NÍVEL 1 — ENTENDER OS DADOS (exploração)

Aqui você só quer ter certeza que entendeu o banco.

### 1.1 Ver pedidos

```sql
SELECT FIRST 50 *
FROM PEDIDOS
ORDER BY DATAABERTURA DESC;
```

👉 Objetivo:

* Ver estrutura real
* Identificar colunas importantes

---

### 1.2 Ver itens de pedido

```sql
SELECT FIRST 50 *
FROM ITENSPEDIDO;
```

👉 Objetivo:

* Entender como os produtos aparecem

---

### 1.3 Ver produtos

```sql
SELECT FIRST 50 CODIGO, NOME, PRECOVENDA, PRECOCUSTO
FROM PRODUTOS;
```

👉 Objetivo:

* Validar preços e custos

---

## 🟡 NÍVEL 2 — PRIMEIRAS MÉTRICAS (visão geral)

Agora começa a análise de verdade.

---

### 2.1 Faturamento total

```sql
SELECT SUM(VALORTOTAL) AS faturamento_total
FROM PEDIDOS
WHERE DATADELETE IS NULL;
```

---

### 2.2 Quantidade de pedidos

```sql
SELECT COUNT(*) AS total_pedidos
FROM PEDIDOS
WHERE DATADELETE IS NULL;
```

---

### 2.3 Ticket médio

```sql
SELECT 
    SUM(VALORTOTAL) / COUNT(*) AS ticket_medio
FROM PEDIDOS
WHERE DATADELETE IS NULL;
```

---

## 🟠 NÍVEL 3 — ANÁLISE TEMPORAL

Agora você começa a entender comportamento.

---

### 3.1 Faturamento por dia

```sql
SELECT 
    CAST(DATAABERTURA AS DATE) AS dia,
    SUM(VALORTOTAL) AS faturamento
FROM PEDIDOS
WHERE DATADELETE IS NULL
GROUP BY dia
ORDER BY dia;
```

---

### 3.2 Faturamento por hora

```sql
SELECT 
    EXTRACT(HOUR FROM DATAABERTURA) AS hora,
    SUM(VALORTOTAL) AS faturamento
FROM PEDIDOS
WHERE DATADELETE IS NULL
GROUP BY hora
ORDER BY hora;
```

👉 Aqui você descobre horário de pico

---

## 🔵 NÍVEL 4 — ANÁLISE DE PRODUTOS

Agora começa a ficar interessante.

---

### 4.1 Produtos mais vendidos

```sql
SELECT 
    pr.NOME,
    SUM(i.QUANTIDADE) AS total_vendido
FROM ITENSPEDIDO i
JOIN PRODUTOS pr ON i.CODIGOPRODUTO = pr.CODIGO
GROUP BY pr.NOME
ORDER BY total_vendido DESC;
```

---

### 4.2 Faturamento por produto

```sql
SELECT 
    pr.NOME,
    SUM(i.VALORTOTAL) AS faturamento
FROM ITENSPEDIDO i
JOIN PRODUTOS pr ON i.CODIGOPRODUTO = pr.CODIGO
GROUP BY pr.NOME
ORDER BY faturamento DESC;
```

---

## 🔴 NÍVEL 5 — LUCRATIVIDADE (OURO)

Agora você sai do básico e entra no estratégico.

---

### 5.1 Lucro por produto

```sql
SELECT 
    pr.NOME,
    SUM(i.QUANTIDADE * (pr.PRECOVENDA - pr.PRECOCUSTO)) AS lucro
FROM ITENSPEDIDO i
JOIN PRODUTOS pr ON i.CODIGOPRODUTO = pr.CODIGO
GROUP BY pr.NOME
ORDER BY lucro DESC;
```

---

### 5.2 Margem por produto

```sql
SELECT 
    pr.NOME,
    AVG(pr.PRECOVENDA - pr.PRECOCUSTO) AS margem_media
FROM PRODUTOS pr
GROUP BY pr.NOME
ORDER BY margem_media DESC;
```

---

## 🟣 NÍVEL 6 — CLIENTES

---

### 6.1 Clientes que mais compram

```sql
SELECT 
    c.NOME,
    COUNT(p.CODIGO) AS total_pedidos
FROM PEDIDOS p
JOIN CONTATOS c ON p.CODIGOCONTATOCLIENTE = c.CODIGO
GROUP BY c.NOME
ORDER BY total_pedidos DESC;
```

---

### 6.2 Ticket médio por cliente

```sql
SELECT 
    c.NOME,
    AVG(p.VALORTOTAL) AS ticket_medio
FROM PEDIDOS p
JOIN CONTATOS c ON p.CODIGOCONTATOCLIENTE = c.CODIGO
GROUP BY c.NOME
ORDER BY ticket_medio DESC;
```

---

## 🟤 NÍVEL 7 — PAGAMENTOS

---

### 7.1 Uso por forma de pagamento

```sql
SELECT 
    f.DESCRICAO,
    COUNT(*) AS quantidade
FROM PAGAMENTOS pg
JOIN FORMASPAGAMENTO f ON pg.CODIGOFORMAPAGAMENTO = f.CODIGO
GROUP BY f.DESCRICAO
ORDER BY quantidade DESC;
```

---

### 7.2 Valor por forma de pagamento

```sql
SELECT 
    f.DESCRICAO,
    SUM(pg.VALOR) AS total
FROM PAGAMENTOS pg
JOIN FORMASPAGAMENTO f ON pg.CODIGOFORMAPAGAMENTO = f.CODIGO
GROUP BY f.DESCRICAO
ORDER BY total DESC;
```

---

## ⚫ NÍVEL 8 — DELIVERY

---

### 8.1 Tempo médio de entrega

```sql
SELECT 
    AVG(DATEDIFF(MINUTE FROM PREPAROINICIADOEM TO ENTREGUEEM)) AS tempo_medio
FROM DELIVERY
WHERE ENTREGUEEM IS NOT NULL;
```

---

# 🧠 COMO EVOLUIR DE VERDADE

Depois desse roadmap, você começa a cruzar dados:

### 🔥 Exemplos avançados

* Produto mais vendido por horário
* Ticket médio por dia da semana
* Lucro por período
* Tempo de entrega vs valor do pedido

---

# ⚠️ DICAS IMPORTANTES

1. Sempre filtre:

```sql
WHERE DATADELETE IS NULL
```

2. Sempre valide:

* valores negativos
* pedidos cancelados

3. Não confie cegamente:

* dados podem estar inconsistentes

---

# 🚀 RESUMO

Esse roadmap te leva de:

👉 “não sei nada do banco”
até
👉 “sei como o restaurante ganha dinheiro”

---

Se quiser, o próximo passo mais poderoso é:

👉 montar um **dashboard (tipo Power BI ou Metabase)**
👉 ou criar queries **ainda mais avançadas (nível gestor)**

Só me fala que caminho você quer seguir.
