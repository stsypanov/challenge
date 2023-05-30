package com.dws.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.TransferRequest;
import com.dws.challenge.domain.TransferResponse;
import com.dws.challenge.repository.AccountsRepository;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.assertj.core.matcher.AssertionMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@WebAppConfiguration
class AccountsControllerTest {

  private MockMvc mockMvc;

  @Autowired
  private AccountsService accountsService;

  @Autowired
  private AccountsRepository accountsRepository;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private NotificationService notificationService;

  @BeforeEach
  void prepareMockMvc() {
    this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

    // Reset the existing accounts before each test.
    accountsRepository.clearAccounts();
  }

  @Test
  void createAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    Account account = accountsService.getAccount("Id-123");
    assertThat(account.getAccountId()).isEqualTo("Id-123");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  void createDuplicateAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNoAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNoBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":\"Id-123\"}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNoBody() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNegativeBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":\"Id-123\",\"balance\":-1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountEmptyAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":\"\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void getAccount() throws Exception {
    String uniqueAccountId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueAccountId, new BigDecimal("123.45"));
    this.accountsService.createAccount(account);
    this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId))
            .andExpect(status().isOk())
            .andExpect(
                    content().string("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":123.45}"));
  }

  @Test
  void transferIllegalAmount() throws Exception {
    var random = ThreadLocalRandom.current();
    var fromAccountId = String.valueOf(random.nextLong());
    var toAccountId = String.valueOf(random.nextLong());
    var fromAccount = new Account(fromAccountId, BigDecimal.TEN);
    var toAccount = new Account(toAccountId, BigDecimal.ZERO);

    createAccount(fromAccount);
    createAccount(toAccount);

    var transferRequest = new TransferRequest(fromAccountId, toAccountId, BigDecimal.ZERO);

    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(transferRequest))
            )
            .andExpect(status().isBadRequest())
            .andDo(result -> {
              var response = result.getResolvedException().getMessage();
              assertThat(response).contains("Transferred amount must be positive.");
            });
  }

  @Test
  void transferFromDoesNotExist() throws Exception {
    var random = ThreadLocalRandom.current();
    var fromAccountId = String.valueOf(random.nextLong());
    var toAccountId = String.valueOf(random.nextLong());

    createAccount(new Account(toAccountId, BigDecimal.ZERO));

    var transferRequest = new TransferRequest(fromAccountId, toAccountId, BigDecimal.ONE);

    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(transferRequest))
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().string(matcherForResponseMessage("'From' account does not exist")));
  }

  @Test
  void transferToDoesNotExist() throws Exception {
    var random = ThreadLocalRandom.current();
    var fromAccountId = String.valueOf(random.nextLong());
    var toAccountId = String.valueOf(random.nextLong());

    createAccount(new Account(fromAccountId, BigDecimal.ZERO));

    var transferRequest = new TransferRequest(fromAccountId, toAccountId, BigDecimal.ONE);

    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(transferRequest))
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().string(matcherForResponseMessage("'To' account does not exist")));
  }

  @Test
  void transferSuccessful() {
    var fromAmount = BigDecimal.TEN;
    var toAmount = BigDecimal.ZERO;
    var transferred = BigDecimal.ONE;

    var random = ThreadLocalRandom.current();
    var fromAccountId = String.valueOf(random.nextLong());
    var toAccountId = String.valueOf(random.nextLong());
    var fromAccount = new Account(fromAccountId, fromAmount);
    var toAccount = new Account(toAccountId, toAmount);

    createAccount(fromAccount);
    createAccount(toAccount);

    var transferRequest = new TransferRequest(fromAccountId, toAccountId, transferred);

    transfer(transferRequest);

    assertThat(this.accountsService.getAccount(fromAccountId).getBalance()).isEqualTo(fromAmount.subtract(transferred));
    assertThat(this.accountsService.getAccount(toAccountId).getBalance()).isEqualTo(toAmount.add(transferred));
  }

  @Test
  void transferTwice() {
    var fromAmount = BigDecimal.TEN;
    var toAmount = BigDecimal.ZERO;
    var transferred = BigDecimal.ONE;

    var random = ThreadLocalRandom.current();
    var fromAccountId = String.valueOf(random.nextLong());
    var toAccountId = String.valueOf(random.nextLong());
    var fromAccount = new Account(fromAccountId, fromAmount);
    var toAccount = new Account(toAccountId, toAmount);

    createAccount(fromAccount);
    createAccount(toAccount);

    var transferRequest = new TransferRequest(fromAccountId, toAccountId, transferred);

    transfer(transferRequest);
    transfer(transferRequest);

    final var two = BigDecimal.valueOf(2);
    assertThat(this.accountsService.getAccount(fromAccountId).getBalance()).isEqualTo(fromAmount.subtract(transferred.multiply(two)));
    assertThat(this.accountsService.getAccount(toAccountId).getBalance()).isEqualTo(toAmount.add(transferred.multiply(two)));
  }

  @Test
  @DisplayName("Lot's of users try to transfer money from one account into another one, everything is expected to be transferred")
  void transferEverythingByChunksConcurrently() {
    var fromAmount = BigDecimal.TEN;
    var toAmount = BigDecimal.ZERO;
    var transferred = BigDecimal.ONE;

    var random = ThreadLocalRandom.current();
    var fromAccountId = String.valueOf(random.nextLong());
    var toAccountId = String.valueOf(random.nextLong());
    var fromAccount = new Account(fromAccountId, fromAmount);
    var toAccount = new Account(toAccountId, toAmount);

    createAccount(fromAccount);
    createAccount(toAccount);

    var transferRequest = new TransferRequest(fromAccountId, toAccountId, transferred);

    var concurrentRequestCount = 10;
    var executor = Executors.newCachedThreadPool();
    var startLatch = new CountDownLatch(1);
    var shutdownLatch = new CountDownLatch(concurrentRequestCount);
    try {
      for (int i = 0; i < concurrentRequestCount; i++) {
        submit(executor, startLatch, transferRequest, shutdownLatch);
      }
      startLatch.countDown();
    } finally {
      await(shutdownLatch);
      executor.shutdown();
    }

    assertThat(this.accountsService.getAccount(fromAccountId).getBalance()).isEqualTo(toAmount);
    assertThat(this.accountsService.getAccount(toAccountId).getBalance()).isEqualTo(fromAmount);
  }

  @Test
  @DisplayName("5 users try to transfer money from one account into their own ones: expect 5 accs to have 1 and source 5")
  void transferEverythingByChunksConcurrently_intoMultipleAccs() {
    var fromAmount = BigDecimal.TEN;
    var toAmount = BigDecimal.ZERO;
    var transferred = BigDecimal.ONE;

    var concurrentRequestCount = 5;

    var toAccounts = new ArrayList<String>();
    var random = ThreadLocalRandom.current();
    for (int i = 0; i < concurrentRequestCount; i++) {
      var toAccountId = String.valueOf(random.nextLong());
      var toAccount = new Account(toAccountId, toAmount);
      createAccount(toAccount);
      toAccounts.add(toAccountId);
    }

    var fromAccountId = String.valueOf(random.nextLong());
    var fromAccount = new Account(fromAccountId, fromAmount);

    createAccount(fromAccount);

    var executor = Executors.newCachedThreadPool();
    var startLatch = new CountDownLatch(1);
    var shutdownLatch = new CountDownLatch(concurrentRequestCount);
    try {
      for (String toAccountId : toAccounts) {
        var transferRequest = new TransferRequest(fromAccountId, toAccountId, transferred);
        submit(executor, startLatch, transferRequest, shutdownLatch);
      }
      startLatch.countDown();
    } finally {
      await(shutdownLatch);
      executor.shutdown();
    }

    for (String toAccountId : toAccounts) {
      assertThat(this.accountsService.getAccount(toAccountId).getBalance()).isEqualTo(transferred);
    }
    assertThat(this.accountsService.getAccount(fromAccountId).getBalance()).isEqualTo(BigDecimal.valueOf(5));
  }

  @Test
  @DisplayName("Multiple users transfer money concurrently, there shouldn't be overdraft")
  void transferEverythingByChunksConcurrently_dontGetOverdraft() {
    var fromAmount = BigDecimal.TEN;
    var toAmount = BigDecimal.ZERO;
    var transferred = BigDecimal.valueOf(5);

    var random = ThreadLocalRandom.current();
    var fromAccountId = String.valueOf(random.nextLong());
    var toAccountId = String.valueOf(random.nextLong());
    var fromAccount = new Account(fromAccountId, fromAmount);
    var toAccount = new Account(toAccountId, toAmount);

    createAccount(fromAccount);
    createAccount(toAccount);

    var transferRequest = new TransferRequest(fromAccountId, toAccountId, transferred);

    var concurrentRequestCount = 10;
    var executor = Executors.newCachedThreadPool();
    var startLatch = new CountDownLatch(1);
    var shutdownLatch = new CountDownLatch(concurrentRequestCount);
    try {
      for (int i = 0; i < concurrentRequestCount; i++) {
        submit(executor, startLatch, transferRequest, shutdownLatch);
      }
      startLatch.countDown();
    } finally {
      await(shutdownLatch);
      executor.shutdown();
    }

    assertThat(this.accountsService.getAccount(fromAccountId).getBalance()).isEqualTo(toAmount);
    assertThat(this.accountsService.getAccount(toAccountId).getBalance()).isEqualTo(fromAmount);
  }

  @Test
  @DisplayName("5 users transfer too much money from one account into their own ones: expect 2 accs to have 5 and source 0")
  void transferEverythingByChunksConcurrently_intoMultipleAccs_noOverDraft() {
    var fromAmount = BigDecimal.TEN;
    var toAmount = BigDecimal.ZERO;
    var transferred = BigDecimal.valueOf(5);

    var concurrentRequestCount = 5;

    var toAccounts = new ArrayList<String>();
    var random = ThreadLocalRandom.current();
    for (int i = 0; i < concurrentRequestCount; i++) {
      var toAccountId = String.valueOf(random.nextLong());
      var toAccount = new Account(toAccountId, toAmount);
      createAccount(toAccount);
      toAccounts.add(toAccountId);
    }

    var fromAccountId = String.valueOf(random.nextLong());
    var fromAccount = new Account(fromAccountId, fromAmount);

    createAccount(fromAccount);

    var executor = Executors.newCachedThreadPool();
    var startLatch = new CountDownLatch(1);
    var shutdownLatch = new CountDownLatch(concurrentRequestCount);
    try {
      for (String toAccountId : toAccounts) {
        var transferRequest = new TransferRequest(fromAccountId, toAccountId, transferred);
        submit(executor, startLatch, transferRequest, shutdownLatch);
      }
      startLatch.countDown();
    } finally {
      await(shutdownLatch);
      executor.shutdown();
    }

    int succeededTransferCount = 0;
    int failedTransferCount = 0;
    for (String toAccountId : toAccounts) {
      var balance = this.accountsService.getAccount(toAccountId).getBalance();
      if (balance.compareTo(transferred) == 0) {
        succeededTransferCount++;
      } else if (balance.compareTo(BigDecimal.ZERO) == 0) {
        failedTransferCount++;
      } else {
        throw new AssertionError("Account " + toAccountId + " received unexpected transfer " + balance);
      }
    }
    assertThat(succeededTransferCount).isEqualTo(2);
    assertThat(failedTransferCount).isEqualTo(3);
    assertThat(this.accountsService.getAccount(fromAccountId).getBalance()).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("Money is transferred from acc1 to acc2 and back concurrently, verify no dead lock and resulting balance is the same")
  void transferBidirectionally_noDeadLock() {
    var fromAmount = BigDecimal.TEN;
    var toAmount = fromAmount;
    var transferred = BigDecimal.ONE;

    var random = ThreadLocalRandom.current();
    var fromAccountId = String.valueOf(random.nextLong());
    var toAccountId = String.valueOf(random.nextLong());
    var fromAccount = new Account(fromAccountId, fromAmount);
    var toAccount = new Account(toAccountId, toAmount);

    createAccount(fromAccount);
    createAccount(toAccount);

    var transferRequest = new TransferRequest(fromAccountId, toAccountId, transferred);
    var reverseTransferRequest = new TransferRequest(toAccountId, fromAccountId, transferred);

    var concurrentRequestCount = 10;
    var executor = Executors.newCachedThreadPool();
    var startLatch = new CountDownLatch(1);
    var shutdownLatch = new CountDownLatch(concurrentRequestCount);
    try {
      for (int i = 0; i < concurrentRequestCount; i++) {
        if (i % 2 == 0) { //evens for direct transfer
          submit(executor, startLatch, transferRequest, shutdownLatch);
        } else { //odds for reverse transfer
          submit(executor, startLatch, reverseTransferRequest, shutdownLatch);
        }
      }
      startLatch.countDown();
    } finally {
      await(shutdownLatch);
      executor.shutdown();
    }

    var balance1 = this.accountsService.getAccount(fromAccountId).getBalance();
    var balance2 = this.accountsService.getAccount(toAccountId).getBalance();

    assertThat(balance1.add(balance2)).isEqualTo(fromAmount.multiply(BigDecimal.valueOf(2)));

    assertThat(this.accountsService.getAccount(fromAccountId).getBalance()).isEqualTo(fromAmount);
    assertThat(this.accountsService.getAccount(toAccountId).getBalance()).isEqualTo(toAmount);
  }

  private void submit(ExecutorService executor, CountDownLatch startLatch, TransferRequest transferRequest, CountDownLatch shutdownLatch) {
    executor.submit(() -> {
      await(startLatch);
      tryTransfer(transferRequest, shutdownLatch);
    });
  }


  private void tryTransfer(TransferRequest transferRequest, CountDownLatch shutdownLatch) {
    transfer(transferRequest);
    shutdownLatch.countDown();
  }

  @SneakyThrows
  private void await(CountDownLatch countDownLatch) {
    countDownLatch.await(10, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private void transfer(TransferRequest transferRequest) {
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(transferRequest))
            )
            .andExpect(status().isOk())
            .andExpect(content().string(matcherForResponseMessage("Success")));
  }

  @SneakyThrows
  private void createAccount(Account fromAccount) {
    this.mockMvc.perform(
            post("/v1/accounts/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(fromAccount))
    ).andExpect(status().isCreated());
  }

  private AssertionMatcher<String> matcherForResponseMessage(String message) {
    return new AssertionMatcher<>() {
      @Override
      @SneakyThrows
      public void assertion(String actual) {
        var response = objectMapper.readValue(actual, TransferResponse.class);
        assertThat(response).isNotNull().extracting(TransferResponse::getMessage).isEqualTo(message);
      }
    };
  }
}
