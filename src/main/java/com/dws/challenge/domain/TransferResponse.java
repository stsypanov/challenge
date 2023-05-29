package com.dws.challenge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TransferResponse {
  private final String message;

  @JsonCreator
  public TransferResponse(@JsonProperty("message") String message) {
    this.message = message;
  }

}
