package com.wex.payments.service;

import com.wex.payments.domain.PurchaseTransaction;
import com.wex.payments.dto.ConvertedPurchaseTransactionResponse;
import com.wex.payments.dto.CreatePurchaseTransactionRequest;
import com.wex.payments.dto.PurchaseTransactionResponse;
import com.wex.payments.exception.CurrencyConversionNotAvailableException;
import com.wex.payments.exception.PurchaseNotFoundException;
import com.wex.payments.repository.PurchaseTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseTransactionServiceTest {

    @Test
    void createPurchaseRoundsAmountAndPersistsTransaction() {
        InMemoryPurchaseTransactionRepository repository = new InMemoryPurchaseTransactionRepository();
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        PurchaseTransactionService service = new PurchaseTransactionService(repository, exchangeRateClient);

        CreatePurchaseTransactionRequest request = new CreatePurchaseTransactionRequest(
                "Office supplies",
                LocalDate.of(2026, 3, 15),
                new BigDecimal("123.456")
        );

        PurchaseTransactionResponse response = service.createPurchase(request);

        assertThat(repository.savedTransactions).hasSize(1);
        assertThat(repository.savedTransactions.getFirst().getPurchaseAmountUsd()).isEqualByComparingTo("123.46");
        assertThat(response.purchaseAmountUsd()).isEqualByComparingTo("123.46");
        assertThat(response.id()).isNotNull();
    }

    @Test
    void getConvertedPurchaseReturnsConvertedAmount() {
        InMemoryPurchaseTransactionRepository repository = new InMemoryPurchaseTransactionRepository();
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        PurchaseTransactionService service = new PurchaseTransactionService(repository, exchangeRateClient);

        PurchaseTransaction transaction = repository.seed("Hotel", LocalDate.of(2026, 2, 10), new BigDecimal("100.00"));
        exchangeRateClient.quote = Optional.of(new ExchangeRateQuote("Canada-Dollar", LocalDate.of(2026, 1, 31), new BigDecimal("1.4234")));

        ConvertedPurchaseTransactionResponse response = service.getConvertedPurchase(transaction.getId(), "Canada-Dollar");

        assertThat(response.exchangeRate()).isEqualByComparingTo("1.4234");
        assertThat(response.convertedAmount()).isEqualByComparingTo("142.34");
        assertThat(response.exchangeRateDate()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void getConvertedPurchaseRejectsMissingPurchase() {
        PurchaseTransactionService service = new PurchaseTransactionService(
                new InMemoryPurchaseTransactionRepository(),
                new StubExchangeRateClient()
        );

        assertThatThrownBy(() -> service.getConvertedPurchase(UUID.randomUUID(), "Canada-Dollar"))
                .isInstanceOf(PurchaseNotFoundException.class);
    }

    @Test
    void getConvertedPurchaseRejectsMissingExchangeRate() {
        InMemoryPurchaseTransactionRepository repository = new InMemoryPurchaseTransactionRepository();
        StubExchangeRateClient exchangeRateClient = new StubExchangeRateClient();
        PurchaseTransactionService service = new PurchaseTransactionService(repository, exchangeRateClient);

        PurchaseTransaction transaction = repository.seed("Hotel", LocalDate.of(2026, 2, 10), new BigDecimal("100.00"));
        exchangeRateClient.quote = Optional.empty();

        assertThatThrownBy(() -> service.getConvertedPurchase(transaction.getId(), "Canada-Dollar"))
                .isInstanceOf(CurrencyConversionNotAvailableException.class)
                .hasMessageContaining("cannot be converted");
    }

    private static final class StubExchangeRateClient extends ExchangeRateClient {
        private Optional<ExchangeRateQuote> quote = Optional.empty();

        private StubExchangeRateClient() {
            super(null, "/unused");
        }

        @Override
        public Optional<ExchangeRateQuote> findExchangeRate(String countryCurrency, LocalDate purchaseDate) {
            return quote;
        }
    }

    private static final class InMemoryPurchaseTransactionRepository implements PurchaseTransactionRepository {
        private final List<PurchaseTransaction> savedTransactions = new ArrayList<>();

        PurchaseTransaction seed(String description, LocalDate transactionDate, BigDecimal purchaseAmountUsd) {
            PurchaseTransaction transaction = new PurchaseTransaction(description, transactionDate, purchaseAmountUsd);
            assignId(transaction, UUID.randomUUID());
            savedTransactions.add(transaction);
            return transaction;
        }

        @Override
        public <S extends PurchaseTransaction> S save(S entity) {
            if (entity.getId() == null) {
                assignId(entity, UUID.randomUUID());
            }
            savedTransactions.add(entity);
            return entity;
        }

        @Override
        public Optional<PurchaseTransaction> findById(UUID uuid) {
            return savedTransactions.stream().filter(transaction -> transaction.getId().equals(uuid)).findFirst();
        }

        private void assignId(PurchaseTransaction transaction, UUID id) {
            try {
                var field = PurchaseTransaction.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(transaction, id);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public List<PurchaseTransaction> findAll() {
            return List.copyOf(savedTransactions);
        }

        @Override
        public List<PurchaseTransaction> findAllById(Iterable<UUID> uuids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            return savedTransactions.size();
        }

        @Override
        public void deleteById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(PurchaseTransaction entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllById(Iterable<? extends UUID> uuids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll(Iterable<? extends PurchaseTransaction> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll() {
            savedTransactions.clear();
        }

        @Override
        public boolean existsById(UUID uuid) {
            return savedTransactions.stream().anyMatch(transaction -> transaction.getId().equals(uuid));
        }

        @Override
        public <S extends PurchaseTransaction> List<S> saveAll(Iterable<S> entities) {
            List<S> saved = new ArrayList<>();
            for (S entity : entities) {
                saved.add(save(entity));
            }
            return saved;
        }

        @Override
        public void flush() {
        }

        @Override
        public <S extends PurchaseTransaction> S saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public <S extends PurchaseTransaction> List<S> saveAllAndFlush(Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(Iterable<PurchaseTransaction> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<UUID> uuids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllInBatch() {
            savedTransactions.clear();
        }

        @Override
        public PurchaseTransaction getOne(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PurchaseTransaction getById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PurchaseTransaction getReferenceById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends PurchaseTransaction> Optional<S> findOne(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends PurchaseTransaction> List<S> findAll(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends PurchaseTransaction> List<S> findAll(org.springframework.data.domain.Example<S> example,
                                                              org.springframework.data.domain.Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends PurchaseTransaction> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example,
                                                                                               org.springframework.data.domain.Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PurchaseTransaction> findAll(org.springframework.data.domain.Sort sort) {
            return findAll();
        }

        @Override
        public org.springframework.data.domain.Page<PurchaseTransaction> findAll(org.springframework.data.domain.Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends PurchaseTransaction> long count(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends PurchaseTransaction> boolean exists(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends PurchaseTransaction, R> R findBy(org.springframework.data.domain.Example<S> example,
                                                           java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException();
        }
    }
}
