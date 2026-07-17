package com.firebird_data_extractor.www.dto;

/**
 * Item do catálogo de produtos para a busca global.
 *
 * id        → PRODUTODETALHE.CODIGO (usado em /product-details/:id)
 * idProduto → PRODUTOS.CODIGO (usado em /insumo-details/:id quando tipo = 2)
 * tipo      → PRODUTOTIPO.CODIGO (1=Produto, 2=Insumo, 3=Complemento,
 *             4=Combo, 5=Produto por Tamanho)
 */
public record ProdutoCatalogoDTO(
    String id,
    Integer idProduto,
    String nome,
    String etiqueta,
    Integer tipo
) {}
