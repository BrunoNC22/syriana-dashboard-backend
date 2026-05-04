package com.firebird_data_extractor.www.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.firebird_data_extractor.www.models.TableInfo;
import com.firebird_data_extractor.www.repositories.MetadataRepository;

@RestController
@RequestMapping("/metadata")
public class MetadataController {
    private final MetadataRepository metadataRepository;

    public MetadataController(MetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    @GetMapping
    public List<TableInfo> getMetadata() {
        return metadataRepository.getDatabaseMetadata();
    }
}
