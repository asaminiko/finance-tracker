package com.tracker.service;

import com.tracker.calc.BaseInterestCalculator;
import com.tracker.calc.BonusRateDecorator;
import com.tracker.calc.InterestCalculator;
import com.tracker.calc.ScheduleBuilder;
import com.tracker.calc.ScheduleRow;
import com.tracker.calc.TaxDecorator;
import com.tracker.domain.Account;
import com.tracker.domain.BankProduct;
import com.tracker.domain.NotFoundException;
import com.tracker.domain.ProductKind;
import com.tracker.domain.ProductStatus;
import com.tracker.domain.SourceType;
import com.tracker.domain.SystemCategories;
import com.tracker.domain.TransactionType;
import com.tracker.domain.ValidationException;
import com.tracker.repo.ProductRepository;
import com.tracker.service.dto.InterestRequest;
import com.tracker.service.dto.ProductRequest;
import com.tracker.service.dto.ProductView;
import com.tracker.service.dto.ReconciliationResponse;
import com.tracker.service.dto.ReconciliationRow;
import com.tracker.service.dto.ReconciliationSummary;
import com.tracker.service.dto.TransactionView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DepositService {

    private static final BigDecimal MAX_RATE = new BigDecimal("100");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final ProductRepository products;
    private final LedgerService ledger;
    private final LedgerQueries queries;
    private final BigDecimal taxRate;
    private final Clock clock;

    public DepositService(
            ProductRepository products,
            LedgerService ledger,
            LedgerQueries queries,
            BigDecimal taxRate,
            Clock clock) {
        this.products = products;
        this.ledger = ledger;
        this.queries = queries;
        this.taxRate = taxRate;
        this.clock = clock;
    }

    public ProductView createProduct(ProductRequest request) {
        ProductKind kind = parseKind(request.kind());
        String name = Validation.requireText(request.name(), "Назва продукту не може бути порожньою");
        if (name.length() > 60) {
            throw new ValidationException("Назва продукту не може перевищувати 60 символів");
        }
        if (request.accountId() == null) {
            throw new ValidationException("Оберіть рахунок");
        }
        BigDecimal amount = Validation.positiveMoney(request.amount(), "Вкажіть суму");
        BigDecimal ratePct = validatePercent(request.ratePct(), "Вкажіть річну ставку", "Ставка");
        BigDecimal bonusPct = request.bonusPct() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)
                : validatePercent(request.bonusPct(), "Вкажіть бонусну ставку", "Бонус");
        boolean taxable = Boolean.TRUE.equals(request.taxable());
        if (kind == ProductKind.CREDIT) {
            if (bonusPct.signum() != 0) {
                throw new ValidationException("Для кредиту бонусна ставка недоступна");
            }
            if (taxable) {
                throw new ValidationException("Для кредиту податок недоступний");
            }
        }
        LocalDate openDate = Validation.date(request.openDate());
        if (request.termMonths() == null || request.termMonths() < 1 || request.termMonths() > 600) {
            throw new ValidationException("Строк має бути від 1 до 600 місяців");
        }
        Account account = ledger.requireAccount(request.accountId());
        LocalDate today = LocalDate.now(clock);
        BankProduct saved = products.insert(
                kind, name, account, amount, ratePct, bonusPct, taxable, openDate, request.termMonths(), today);
        return toView(saved);
    }

    public List<ProductView> listProducts() {
        LocalDate today = LocalDate.now(clock);
        return products.findAll(today).stream().map(DepositService::toView).toList();
    }

    public TransactionView recordInterest(long productId, InterestRequest request) {
        LocalDate date = Validation.date(request.date());
        BigDecimal amount = Validation.positiveMoney(request.amount(), "Вкажіть суму");
        LocalDate today = LocalDate.now(clock);
        BankProduct product = products.find(productId, today)
                .orElseThrow(() -> new NotFoundException("Продукт не знайдено"));
        YearMonth openMonth = YearMonth.from(product.openDate());
        YearMonth firstMonth = openMonth.plusMonths(1);
        YearMonth lastMonth = openMonth.plusMonths(product.termMonths());
        YearMonth dateMonth = YearMonth.from(date);
        if (dateMonth.isBefore(firstMonth) || dateMonth.isAfter(lastMonth)) {
            throw new ValidationException(
                    "Місяць дати має бути в межах строку продукту (" + firstMonth + " .. " + lastMonth + ")");
        }
        product.checkRecordInterest(date, today);
        if (product.kind() == ProductKind.DEPOSIT) {
            return ledger.recordSystemTransaction(
                    product.account(),
                    SystemCategories.DEPOSIT_INTEREST,
                    TransactionType.INCOME,
                    amount,
                    date,
                    "Відсотки за вкладом: " + product.name(),
                    SourceType.PRODUCT,
                    product.id());
        }
        return ledger.recordSystemTransaction(
                product.account(),
                SystemCategories.CREDIT_INTEREST,
                TransactionType.EXPENSE,
                amount.negate(),
                date,
                "Відсотки за кредитом: " + product.name(),
                SourceType.PRODUCT,
                product.id());
    }

    public ProductView closeProduct(long productId) {
        LocalDate today = LocalDate.now(clock);
        BankProduct product = products.find(productId, today)
                .orElseThrow(() -> new NotFoundException("Продукт не знайдено"));
        product.close();
        products.updateStatus(productId, ProductStatus.CLOSED);
        return toView(product);
    }

    public ReconciliationResponse reconcile(long productId) {
        LocalDate today = LocalDate.now(clock);
        BankProduct product = products.find(productId, today)
                .orElseThrow(() -> new NotFoundException("Продукт не знайдено"));
        InterestCalculator calculator = new BaseInterestCalculator();
        if (product.kind() == ProductKind.DEPOSIT) {
            if (product.bonusPct().signum() > 0) {
                calculator = new BonusRateDecorator(calculator, product.bonusPct());
            }
            if (product.taxable()) {
                calculator = new TaxDecorator(calculator, taxRate);
            }
        }
        List<ScheduleRow> schedule = ScheduleBuilder.build(
                product.kind(),
                product.amount(),
                product.ratePct(),
                product.termMonths(),
                product.openDate(),
                calculator);
        Map<String, BigDecimal> actualByMonth = queries.interestByMonth(productId);
        YearMonth currentMonth = YearMonth.from(today);
        List<ReconciliationRow> rows = new ArrayList<>(schedule.size());
        BigDecimal summaryExpected = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal summaryActual = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        int comparedCount = 0;
        for (ScheduleRow row : schedule) {
            YearMonth rowMonth = YearMonth.parse(row.month());
            BigDecimal recorded = actualByMonth.get(row.month());
            boolean isFuture = rowMonth.isAfter(currentMonth)
                    || (rowMonth.equals(currentMonth) && recorded == null);
            if (isFuture) {
                rows.add(new ReconciliationRow(
                        row.month(), row.openingBalance(), row.expected(), null, null, "FUTURE"));
            } else {
                BigDecimal actual = recorded != null ? recorded : BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
                BigDecimal difference = actual.subtract(row.expected());
                String status = difference.abs().compareTo(TOLERANCE) <= 0 ? "OK" : "MISMATCH";
                rows.add(new ReconciliationRow(
                        row.month(), row.openingBalance(), row.expected(), actual, difference, status));
                summaryExpected = summaryExpected.add(row.expected());
                summaryActual = summaryActual.add(actual);
                comparedCount++;
            }
        }
        BigDecimal summaryDifference = summaryActual.subtract(summaryExpected);
        String verdict;
        if (comparedCount == 0) {
            verdict = "NONE";
        } else if (summaryDifference.abs().compareTo(TOLERANCE) <= 0) {
            verdict = "EQUAL";
        } else if (summaryDifference.signum() < 0) {
            verdict = "ACTUAL_LESS";
        } else {
            verdict = "ACTUAL_MORE";
        }
        return new ReconciliationResponse(
                toView(product),
                rows,
                new ReconciliationSummary(summaryExpected, summaryActual, summaryDifference, verdict));
    }

    private static ProductKind parseKind(String raw) {
        if ("DEPOSIT".equals(raw)) {
            return ProductKind.DEPOSIT;
        }
        if ("CREDIT".equals(raw)) {
            return ProductKind.CREDIT;
        }
        throw new ValidationException("Вид продукту має бути DEPOSIT або CREDIT");
    }

    private static BigDecimal validatePercent(BigDecimal value, String missingMessage, String fieldLabel) {
        if (value == null) {
            throw new ValidationException(missingMessage);
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new ValidationException(fieldLabel + " має містити не більше 2 знаків після коми");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(MAX_RATE) > 0) {
            throw new ValidationException(fieldLabel + " має бути від 0 до 100");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static ProductView toView(BankProduct product) {
        return new ProductView(
                product.id(),
                product.kind().name(),
                product.name(),
                product.account().id(),
                product.account().name(),
                product.amount(),
                product.ratePct(),
                product.bonusPct(),
                product.taxable(),
                product.openDate(),
                product.termMonths(),
                product.endDate(),
                product.status().name(),
                product.state().code());
    }
}
