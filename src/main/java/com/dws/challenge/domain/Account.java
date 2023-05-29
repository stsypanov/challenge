package com.dws.challenge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.concurrent.Semaphore;

import lombok.*;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class Account {

  @NotNull
  @NotEmpty
  private final String accountId;

  @NotNull
  @DecimalMin(value = "0.0", message = "Initial balance must be positive.")
  private BigDecimal balance;

  @JsonIgnore
  @EqualsAndHashCode.Exclude
  @Getter(AccessLevel.PRIVATE)
  private final Semaphore lock = new Semaphore(1);

  public Account(String accountId) {
    this.accountId = accountId;
    this.balance = BigDecimal.ZERO;
  }

  @JsonCreator
  public Account(@JsonProperty("accountId") String accountId,
    @JsonProperty("balance") BigDecimal balance) {
    this.accountId = accountId;
    this.balance = balance;
  }

  @SneakyThrows
  public void lock() {
    lock.acquire();
  }

  public void unlock() {
    lock.release();
  }
}
