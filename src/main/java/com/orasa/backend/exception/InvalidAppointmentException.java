package com.orasa.backend.exception;

public class InvalidAppointmentException extends RuntimeException{
  public InvalidAppointmentException(String message) {
    super(message);
  }
}
