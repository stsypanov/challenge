package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.TransferRequest;
import com.dws.challenge.domain.TransferStatus;
import com.dws.challenge.repository.AccountsRepository;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class AccountsService {

  private final ConcurrentMap<AccountPairKey, Semaphore> locks = new ConcurrentHashMap<>();

  private final AccountsRepository accountsRepository;
  private final NotificationService notificationService;

  public void createAccount(Account account) {
    this.accountsRepository.createAccount(account);
  }

  public Account getAccount(String accountId) {
    return this.accountsRepository.getAccount(accountId);
  }

  @SneakyThrows
  public TransferStatus transfer(TransferRequest request) {
    var from = getAccount(request.getFromAccountId());
    if (from == null) {
      return TransferStatus.FROM_ACC_MISSING;
    }
    var to = getAccount(request.getToAccountId());
    if (to == null) {
      return TransferStatus.TO_ACC_MISSING;
    }

    final var accountPair = new AccountPairKey(from.toString(), to.toString());
    final var mutualLock = locks.computeIfAbsent(accountPair, key -> new Semaphore(1));
    mutualLock.acquire();
    try {
      from.lock();
      to.lock();
      var fromBalance = from.getBalance();
      var toBalance = to.getBalance();
      var transferAmount = request.getAmount();
      if (fromBalance.compareTo(transferAmount) < 0) {
        return TransferStatus.LIMIT_EXCEEDED;
      }
      from.setBalance(fromBalance.subtract(transferAmount));
      to.setBalance(toBalance.add(transferAmount));
      notificationService.notifyAboutTransfer(from, fromMsg(transferAmount, to));
      notificationService.notifyAboutTransfer(to, toMsg(transferAmount, from));
      return TransferStatus.SUCCESS;
    } finally {
      to.unlock();
      from.unlock();
      mutualLock.release();
      locks.remove(accountPair);
    }
  }

  private String toMsg(BigDecimal transferAmount, Account from) {
    return "Transferred " + transferAmount + " into your account from " + from.getAccountId();
  }

  private String fromMsg(BigDecimal transferAmount, Account to) {
    return "Transferred " + transferAmount + " from your account to " + to.getAccountId();
  }

  @EqualsAndHashCode
  @RequiredArgsConstructor
  private static class AccountPairKey {
    private final String fromId;
    private final String toId;
  }

}
