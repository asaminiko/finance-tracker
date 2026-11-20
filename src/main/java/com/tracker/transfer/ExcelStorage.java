package com.tracker.transfer;

import com.tracker.domain.StorageException;
import com.tracker.domain.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelStorage implements TransactionStorage {

    private static final List<String> HEADERS = List.of("date", "amount", "category", "account", "description");

    @Override
    public byte[] write(List<TransferRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("transactions");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
            }
            int index = 1;
            for (TransferRow item : rows) {
                Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(item.date() == null ? "" : item.date());
                row.createCell(1).setCellValue(new BigDecimal(item.amount()).doubleValue());
                row.createCell(2).setCellValue(item.category() == null ? "" : item.category());
                row.createCell(3).setCellValue(item.account() == null ? "" : item.account());
                row.createCell(4).setCellValue(item.description() == null ? "" : item.description());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new StorageException("Не вдалося сформувати файл Excel: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TransferRow> read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("Файл не є коректним .xlsx");
        }
        XSSFWorkbook workbook;
        try {
            workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new ValidationException("Файл не є коректним .xlsx");
        }
        try (workbook) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ValidationException("Файл не є коректним .xlsx");
            }
            Sheet sheet = workbook.getSheet("transactions");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            verifyHeader(sheet.getRow(0), formatter);
            List<TransferRow> result = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isRowEmpty(row)) {
                    continue;
                }
                result.add(parseRow(rowIndex + 1, row, formatter));
            }
            return result;
        } catch (IOException e) {
            throw new ValidationException("Файл не є коректним .xlsx");
        }
    }

    @Override
    public String mediaType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public String fileExtension() {
        return "xlsx";
    }

    private static void verifyHeader(Row header, DataFormatter formatter) {
        if (header == null) {
            throw new ValidationException(
                    "Перший рядок файлу Excel має містити заголовки: date, amount, category, account, description");
        }
        for (int i = 0; i < HEADERS.size(); i++) {
            Cell cell = header.getCell(i);
            String value = cell == null
                    ? ""
                    : formatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
            if (!HEADERS.get(i).equals(value)) {
                throw new ValidationException(
                        "Перший рядок файлу Excel має містити заголовки: date, amount, category, account, description");
            }
        }
    }

    private static boolean isRowEmpty(Row row) {
        if (row == null || row.getLastCellNum() <= 0) {
            return true;
        }
        int lastCell = Math.max(4, row.getLastCellNum() - 1);
        for (int i = 0; i <= lastCell; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                if (cell.getCellType() == CellType.STRING) {
                    if (!cell.getStringCellValue().isBlank()) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    private static TransferRow parseRow(int rowNumber, Row row, DataFormatter formatter) {
        String parseError = null;

        String date = "";
        Cell dateCell = row.getCell(0);
        if (dateCell != null && dateCell.getCellType() != CellType.BLANK) {
            if (dateCell.getCellType() == CellType.STRING) {
                date = dateCell.getStringCellValue().trim();
            } else if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                date = dateCell.getLocalDateTimeCellValue().toLocalDate().toString();
            } else {
                parseError = "некоректний тип комірки дати";
            }
        }

        String amount = "";
        Cell amountCell = row.getCell(1);
        if (amountCell != null && amountCell.getCellType() != CellType.BLANK) {
            if (amountCell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(amountCell)) {
                amount = BigDecimal.valueOf(amountCell.getNumericCellValue()).toPlainString();
            } else if (amountCell.getCellType() == CellType.STRING) {
                amount = amountCell.getStringCellValue().trim();
            } else if (parseError == null) {
                parseError = "некоректний тип комірки суми";
            }
        }

        String[] textValues = new String[3];
        for (int i = 0; i < 3; i++) {
            Cell cell = row.getCell(i + 2);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                textValues[i] = "";
            } else if (cell.getCellType() == CellType.STRING
                    || (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell))) {
                textValues[i] = formatter.formatCellValue(cell).trim();
            } else {
                textValues[i] = "";
                if (parseError == null) {
                    parseError = "некоректний тип комірки";
                }
            }
        }

        return new TransferRow(
                rowNumber,
                date,
                amount,
                textValues[0],
                textValues[1],
                textValues[2],
                parseError);
    }
}
