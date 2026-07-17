package com.firebird_data_extractor.www.repositories;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.firebird_data_extractor.www.dto.ProdutoCatalogoDTO;

@Repository
public class ProdutoRepository {

    private final JdbcTemplate jdbc;

    public ProdutoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Catálogo completo de produtos ativos (produtos vendáveis, insumos,
     * complementos...) para a busca global do frontend. Volume pequeno
     * (~400 registros ativos) — o filtro por nome acontece no cliente.
     *
     * Nome composto com o tamanho quando o detalhe tem variação
     * (ex.: "Pizza Calabresa Grande").
     */
    public List<ProdutoCatalogoDTO> getCatalogo() {

        return this.jdbc.query(
            """
            SELECT
                pd.CODIGO AS codigo_detalhe,
                p.CODIGO AS codigo_produto,
                p.NOME AS nome,
                pt.DESCRICAO AS tamanho,
                e.DESCRICAO AS etiqueta,
                p.CODIGOPRODUTOTIPO AS tipo
            FROM PRODUTODETALHE pd
            JOIN PRODUTOS p ON p.CODIGO = pd.CODIGOPRODUTO
            LEFT JOIN PRODUTOTAMANHO pt ON pt.CODIGO = pd.CODIGOPRODUTOTAMANHO
            LEFT JOIN ETIQUETAS e ON e.CODIGO = p.CODIGOETIQUETA
            WHERE
                pd.DATADELETE IS NULL
                AND p.DESCONTINUADO = 'N'
            ORDER BY p.NOME
            """,
            (rs, rowNum) -> {

                String nome = rs.getString("nome");
                String tamanho = rs.getString("tamanho");

                if (tamanho != null && !tamanho.isBlank()) {
                    nome = nome + " " + tamanho;
                }

                return new ProdutoCatalogoDTO(
                    String.valueOf(rs.getInt("codigo_detalhe")),
                    rs.getInt("codigo_produto"),
                    nome,
                    rs.getString("etiqueta"),
                    rs.getInt("tipo")
                );
            }
        );
    }
}
