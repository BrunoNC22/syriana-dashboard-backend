package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;

public record FaturamentoPorDiaDTO(
    LocalDate dia,
    Double faturamento
) {}