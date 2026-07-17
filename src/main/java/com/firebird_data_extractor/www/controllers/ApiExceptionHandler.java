package com.firebird_data_extractor.www.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.firebird_data_extractor.www.dto.ErroValidacaoDTO;

/**
 * Converte falhas de validação de parâmetros (IllegalArgumentException
 * lançada pelos controllers) em respostas 400 com a mensagem legível,
 * em vez do 500 genérico do servlet container.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroValidacaoDTO handleIllegalArgument(IllegalArgumentException ex) {
        return new ErroValidacaoDTO(ex.getMessage());
    }
}
