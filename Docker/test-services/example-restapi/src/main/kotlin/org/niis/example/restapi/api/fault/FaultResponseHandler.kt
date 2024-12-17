package org.niis.example.restapi.api.fault;

import org.niis.example.restapi.core.fault.MemberExistsException
import org.niis.example.restapi.core.fault.MemberNotFoundException
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class FaultResponseHandler {

    @ExceptionHandler(MemberNotFoundException::class)
    fun handleMemberNotFoundException(ex: MemberNotFoundException):ResponseEntity<FaultResponse> {
        return ResponseEntity(FaultResponse(ex.message), HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(MemberExistsException::class)
    fun handleMemberExistsException(ex: MemberExistsException):ResponseEntity<FaultResponse> {
        return ResponseEntity(FaultResponse(ex.message), HttpStatus.CONFLICT)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<FaultResponse> {
        return ResponseEntity(FaultResponse(ex.message), HttpStatus.INTERNAL_SERVER_ERROR)
    }
}

data class FaultResponse(val message: String?)
