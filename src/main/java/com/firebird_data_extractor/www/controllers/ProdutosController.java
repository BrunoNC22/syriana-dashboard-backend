package com.firebird_data_extractor.www.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.firebird_data_extractor.www.dto.ProdutoCatalogoDTO;
import com.firebird_data_extractor.www.repositories.ProdutoRepository;

@RestController
@RequestMapping("/produtos")
public class ProdutosController {

    private final ProdutoRepository repository;

    public ProdutosController(ProdutoRepository repo) {
        this.repository = repo;
    }

    @GetMapping
    public List<ProdutoCatalogoDTO> getCatalogo() {
        return this.repository.getCatalogo();
    }
}
