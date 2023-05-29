package com.dws.challenge.web;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.TransferRequest;
import com.dws.challenge.domain.TransferResponse;
import com.dws.challenge.domain.TransferStatus;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.service.AccountsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountsController {

  private final AccountsService accountsService;

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> createAccount(@RequestBody @Valid Account account) {
    log.info("Creating account {}", account);

    try {
    this.accountsService.createAccount(account);
    } catch (DuplicateAccountIdException daie) {
      return new ResponseEntity<>(daie.getMessage(), HttpStatus.BAD_REQUEST);
    }

    return new ResponseEntity<>(HttpStatus.CREATED);
  }

  @GetMapping(path = "/{accountId}")
  public Account getAccount(@PathVariable String accountId) {
    log.info("Retrieving account for id {}", accountId);
    return this.accountsService.getAccount(accountId);
  }

  @PostMapping(path = "/transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TransferResponse> transfer(@RequestBody @Valid TransferRequest request) {
    TransferStatus result = this.accountsService.transfer(request);
    switch (result) {
      case SUCCESS:
        return ResponseEntity.ok(new TransferResponse("Success"));
      case FROM_ACC_MISSING:
        return ResponseEntity.badRequest().body(new TransferResponse("'From' account does not exist"));
      case TO_ACC_MISSING:
        return ResponseEntity.badRequest().body(new TransferResponse("'To' account does not exist"));
      case LIMIT_EXCEEDED:
        return ResponseEntity.badRequest().body(new TransferResponse("Transfer limit exceeded"));
      default:
        return ResponseEntity.internalServerError().body(new TransferResponse("Unexpected transfer status"));
    }
  }

}
