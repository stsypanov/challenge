package com.dws.challenge.domain;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class TransferRequest {
  @NotNull
  @NotEmpty
  private final String fromAccountId;

  @NotNull
  @NotEmpty
  private final String toAccountId;

  @NotNull
  @DecimalMin(value = "0.0", message = "Transferred amount must be positive.", inclusive = false)
  private BigDecimal amount;

  @JsonCreator
  public TransferRequest(
          @JsonProperty("fromAccountId") String fromAccountId,
          @JsonProperty("toAccountId") String toAccountId,
          @JsonProperty("amount") BigDecimal amount
  ) {
    this.fromAccountId = fromAccountId;
    this.toAccountId = toAccountId;
    this.amount = amount;
  }

}
