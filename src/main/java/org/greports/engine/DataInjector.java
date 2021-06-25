package org.greports.engine;

import com.google.common.base.Stopwatch;

import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder;
import org.greports.content.cell.DataCell;
import org.greports.content.cell.HeaderCell;
import org.greports.content.cell.SpecialDataRowCell;
import org.greports.content.header.ReportHeader;
import org.greports.content.row.DataRow;
import org.greports.content.row.SpecialDataRow;
import org.greports.positioning.HorizontalRange;
import org.greports.positioning.VerticalRange;
import org.greports.services.LoggerService;
import org.greports.styles.Style;
import org.greports.styles.stylesbuilders.ReportStylesBuilder;
import org.greports.utils.Utils;
import org.greports.utils.WorkbookUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

class DataInjector {

    public static final int WIDTH_MULTIPLIER = 256;

    private Map<Pair<Style, String>, XSSFCellStyle> stylesCache = new HashMap<>();
    protected final XSSFWorkbook currentWorkbook;
    protected final Data data;
    protected final CreationHelper creationHelper;
    protected LoggerService loggerService;
    protected Map<String, XSSFCellStyle> formatsCache = new HashMap<>();

    public DataInjector(XSSFWorkbook currentWorkbook, Data data, boolean loggerEnabled, Level level) {
        this.currentWorkbook = currentWorkbook;
        this.data = data;
        this.creationHelper = this.currentWorkbook.getCreationHelper();
        this.loggerService = new LoggerService(this.getClass(), loggerEnabled, level);
    }

    public void inject() {
        Sheet sheet = WorkbookUtils.getOrCreateSheet(currentWorkbook, data.getSheetName());
        stylesCache = new HashMap<>();
        injectData(sheet);
    }

    protected void injectData(Sheet sheet) {
        setSheetAttributes(sheet);
        createHeader(sheet);
        createDataRows(sheet);
        createSpecialRows(sheet);
        createRowsGroups(sheet);
        createColumnsGroups(sheet);
        addStyles(sheet);
        adjustColumns(sheet);
    }

    private void setSheetAttributes(Sheet sheet) {
        loggerService.trace("Applying sheet configuration...");
        Configuration configuration = data.getConfiguration();
        sheet.setDisplayZeros(configuration.isDisplayZeros());
        sheet.setDisplayGridlines(data.getConfiguration().isShowGridlines());
        loggerService.trace("Sheet configuration applied");
    }

    private void createHeader(Sheet sheet) {
        loggerService.trace("Creating header...", data.isCreateHeader());
        final Stopwatch headersStopwatch = Stopwatch.createStarted();

        if(data.isCreateHeader()) {
            final ReportHeader header = data.getHeader();
            Row headerRow = WorkbookUtils.getOrCreateRow(sheet, header.getRowIndex() + data.getConfiguration().getVerticalOffset());
            int mergeCount = 0;
            for (int i = 0; i < header.getCells().size(); i++) {
                final HeaderCell headerCell = header.getCells().get(i);
                createHeaderCell(
                        sheet,
                        headerRow,
                        headerCell,
                        i + mergeCount + data.getConfiguration().getHorizontalOffset(),
                        headerCell.getColumnWidth()
                );
                if(headerCell.getColumnWidth() > 1) {
                    mergeCount += headerCell.getColumnWidth() - 1;
                }
            }

            if(header.isColumnFilter()) {
                sheet.setAutoFilter(new CellRangeAddress(headerRow.getRowNum(), headerRow.getRowNum(), 0, header.getCells().size() - 1));
            }

            if(header.isStickyHeader()) {
                sheet.createFreezePane(0, headerRow.getRowNum() + 1, 0, headerRow.getRowNum() + 1);
            }
        }
        loggerService.trace("Header created. Time: " + headersStopwatch.stop(), data.isCreateHeader());
    }

    private void createDataRows(Sheet sheet) {
        loggerService.trace("Creating data rows...");
        final Stopwatch dataRowsStopwatch = Stopwatch.createStarted();

        // First create cells with data
        final Predicate<DataCell> dataCellsPredicate = (DataCell dataCell) -> !dataCell.getValueType().equals(ValueType.FORMULA);
        // After create cells with formulas to can evaluate them
        final Predicate<DataCell> formulaCellsPredicate = (DataCell dataCell) -> dataCell.getValueType().equals(ValueType.FORMULA);
        this.createCells(sheet, dataCellsPredicate);
        this.createCells(sheet, formulaCellsPredicate);

        loggerService.trace("Data rows created. Time: " + dataRowsStopwatch.stop());
    }

    private void createCells(Sheet sheet, Predicate<DataCell> predicate) {
        for (int i = 0; i < data.getDataRows().size(); i++) {
            final DataRow dataRow = data.getDataRow(i);
            Row row = WorkbookUtils.getOrCreateRow(sheet, data.getDataRealStartRow() + i);
            int mergedCellsCount = 0;
            for (int y = 0; y < dataRow.getCells().size(); y++) {
                final DataCell dataCell = dataRow.getCell(y);
                if(predicate.test(dataCell)) {
                    createCell(
                            sheet,
                            row,
                            dataCell,
                            dataCell.isPhysicalPosition()
                                    ? dataCell.getPosition().intValue()
                                    : mergedCellsCount + data.getConfiguration().getHorizontalOffset() + y
                    );
                    if(dataCell.getColumnWidth() > 1) {
                        mergedCellsCount += dataCell.getColumnWidth() - 1;
                    }
                }
            }
        }
    }

    private void createHeaderCell(final Sheet sheet, final Row row, final HeaderCell headerCell, final int cellIndex, final int columnWidth) {
        final Cell cell = row.createCell(cellIndex);
        createColumnsToMerge(sheet, row, cellIndex, columnWidth);
        WorkbookUtils.setCellValue(cell, headerCell.getValue());
    }

    private void createCell(Sheet sheet, Row row, DataCell dataCell, int columnIndex) {
        CellType cellType = CellType.BLANK;
        final ValueType valueType = dataCell.getValueType();
        if(!ValueType.FORMULA.equals(valueType)) {
            if(dataCell.getValue() instanceof Number) {
                cellType = CellType.NUMERIC;
            } else if(dataCell.getValue() instanceof String) {
                cellType = CellType.STRING;
            } else if(dataCell.getValue() instanceof Boolean) {
                cellType = CellType.BOOLEAN;
            }
            final Cell cell = row.createCell(columnIndex, cellType);
            WorkbookUtils.setCellValue(cell, dataCell.getValue());
            setCellFormat(cell, dataCell.getFormat());
        } else {
            cellType = CellType.FORMULA;
            final Cell cell = row.createCell(columnIndex, cellType);
            String formulaString = dataCell.getValue().toString();
            formulaString = replaceFormulaIndexes(row, formulaString);
            cell.setCellFormula(formulaString);
            setCellFormat(cell, dataCell.getFormat());
        }

        createColumnsToMerge(sheet, row, columnIndex, dataCell.getColumnWidth());
    }

    private void createRowsGroups(final Sheet sheet) {
        List<Pair<Integer, Integer>> groupedRows = data.getGroupedRows();

        loggerService.trace("Creating row's groups...", !groupedRows.isEmpty());
        final Stopwatch rowsGroup = Stopwatch.createStarted();

        for(final Pair<Integer, Integer> groupedRow : groupedRows) {
            int startGroup = data.getDataStartRow() + groupedRow.getLeft() + data.getConfiguration().getDataStartRowIndex();
            int endGroup = data.getDataStartRow() + groupedRow.getRight()  + data.getConfiguration().getDataStartRowIndex();
            sheet.groupRow(startGroup, endGroup);
            sheet.setRowGroupCollapsed(startGroup, data.isGroupedRowsDefaultCollapsed());
        }
        loggerService.trace("Row's groups created. Time: " + rowsGroup.stop(), !groupedRows.isEmpty());
    }

    private void createColumnsGroups(final Sheet sheet) {
        final List<Pair<Integer, Integer>> groupedColumns = data.getGroupedColumns();

        loggerService.trace("Creating row's groups...", !groupedColumns.isEmpty());
        final Stopwatch columnsGroup = Stopwatch.createStarted();

        for(final Pair<Integer, Integer> groupedColumn : groupedColumns) {
            final int left = groupedColumn.getLeft() + data.getConfiguration().getHorizontalOffset();
            final int right = groupedColumn.getRight() + data.getConfiguration().getHorizontalOffset();
            sheet.groupColumn(left, right);
            sheet.setColumnGroupCollapsed(left, data.isGroupedColumnsDefaultCollapsed());
        }
        loggerService.trace("Column's groups created. Time: " + columnsGroup.stop(), !groupedColumns.isEmpty());
    }

    private void addStyles(Sheet sheet) {
        final ReportStylesBuilder reportStylesBuilder = data.getStyles().getReportStylesBuilder();
        if(reportStylesBuilder != null) {
            loggerService.trace("Adding styles...");
            final Stopwatch stylesStopwatch = Stopwatch.createStarted();

            final List<Style> styles = reportStylesBuilder.getStyles();
            final short verticalOffset = data.getConfiguration().getVerticalOffset();
            final short horizontalOffset = data.getConfiguration().getHorizontalOffset();
            for (Style style : styles) {
                final VerticalRange verticalRange = style.getRange().getVerticalRange();
                checkRange(verticalRange, sheet);
                final HorizontalRange horizontalRange = style.getRange().getHorizontalRange();
                checkRange(horizontalRange, data);
                for (int i = verticalRange.getStart() + verticalOffset; i <= verticalRange.getEnd() + verticalOffset; i++) {
                    final Row row = sheet.getRow(i);
                    if(row != null) {
                        for (int y = horizontalRange.getStart() + horizontalOffset; y <= horizontalRange.getEnd() + horizontalOffset; y++) {
                            cellApplyStyles(row.getCell(y), style);
                        }
                        if (style.getRowHeight() != null) {
                            row.setHeightInPoints(style.getRowHeight());
                        }
                    }
                }
                if(style.getColumnWidth() != null) {
                    for (int i = horizontalRange.getStart() + horizontalOffset; i <= horizontalRange.getEnd() + horizontalOffset; i++) {
                        sheet.setColumnWidth(i, style.getColumnWidth() * WIDTH_MULTIPLIER);
                    }
                }
            }
            loggerService.trace("Styles added. Time: " + stylesStopwatch.stop());
            loggerService.trace("Total styles: " + sheet.getWorkbook().getNumCellStyles());
        }
    }

    private void checkRange(VerticalRange range, Sheet sheet) {
        if(Objects.isNull(range.getStart())) {
            range.setStart(sheet.getLastRowNum());
        } else if(range.getStart() < 0) {
            range.setStart(sheet.getLastRowNum() + range.getStart());
        }

        if(Objects.isNull(range.getEnd())) {
            range.setEnd(sheet.getLastRowNum());
        } else if(range.getEnd() < 0) {
            range.setEnd(sheet.getLastRowNum() + range.getEnd());
        }
    }

    private void checkRange(HorizontalRange range, Data data) {
        if(Objects.isNull(range.getStart())) {
            range.setStart(data.getColumnsCount() - 1);
        } else if(range.getStart() < 0) {
            range.setStart(data.getColumnsCount() + range.getStart() - 1);
        }

        if(Objects.isNull(range.getEnd())) {
            range.setEnd(data.getColumnsCount() - 1);
        } else if(range.getEnd() < 0) {
            range.setEnd(data.getColumnsCount() + range.getEnd() - 1);
        }
    }

    private void cellApplyStyles(Cell cell, Style style) {
        if(cell != null) {
            XSSFCellStyle cellStyle;
            final Pair<Style, String> styleKey = Pair.of(style, cell.getCellStyle().getDataFormatString());
            if(!stylesCache.containsKey(styleKey) || style.isClonePreviousStyle()) {
                cellStyle = currentWorkbook.createCellStyle();
                cellStyle.setDataFormat(cell.getCellStyle().getDataFormat());
                if(style.isClonePreviousStyle()) {
                    cellStyle.cloneStyleFrom(cell.getCellStyle());
                }

                // Borders
                cellApplyBorderStyles(style, cellStyle);

                // Colors
                cellApplyColorStyles(style, cellStyle);

                // Font
                cellApplyFontStyles(style, cellStyle);

                // Alignment
                cellApplyAlignmentStyles(style, cellStyle);

                // Other
                cellApplyOtherStyles(style, cellStyle);

                stylesCache.put(styleKey, cellStyle);
            } else {
                cellStyle = stylesCache.get(styleKey);
            }
            cell.setCellStyle(cellStyle);
        }
    }

    private void cellApplyOtherStyles(Style style, XSSFCellStyle cellStyle) {
        if(style.getHidden() != null) {
            cellStyle.setHidden(style.getHidden());
        }

        if(style.getIndentation() != null) {
            cellStyle.setIndention(style.getIndentation());
        }

        if(style.getLocked() != null) {
            cellStyle.setLocked(style.getLocked());
        }

        if(style.getQuotePrefixed() != null) {
            cellStyle.setQuotePrefixed(style.getQuotePrefixed());
        }

        if(style.getRotation() != null) {
            cellStyle.setRotation(style.getRotation());
        }

        if(style.getShrinkToFit() != null) {
            cellStyle.setShrinkToFit(style.getShrinkToFit());
        }

        if(style.getWrapText() != null) {
            cellStyle.setWrapText(style.getWrapText());
        }
    }

    private void cellApplyAlignmentStyles(Style style, XSSFCellStyle cellStyle) {
        if(style.getHorizontalAlignment() != null) {
            cellStyle.setAlignment(style.getHorizontalAlignment());
        }
        if(style.getVerticalAlignment() != null) {
            cellStyle.setVerticalAlignment(style.getVerticalAlignment());
        }
    }

    private void cellApplyFontStyles(Style style, XSSFCellStyle cellStyle) {
        if(Utils.anyNotNull(style.getFontName(), style.getFontSize(), style.getFontColor(), style.getBoldFont(), style.getItalicFont(), style.getUnderlineFont(), style.getStrikeoutFont())) {
            XSSFFont font = currentWorkbook.createFont();
            if(style.getFontName() != null) {
                font.setFontName(style.getFontName());
            }
            if(style.getFontSize() != null) {
                font.setFontHeightInPoints(style.getFontSize());
            }
            if(style.getFontColor() != null) {
                font.setColor(new XSSFColor(style.getFontColor()));
            }
            if(style.getBoldFont() != null) {
                font.setBold(style.getBoldFont());
            }
            if(style.getItalicFont() != null) {
                font.setItalic(style.getItalicFont());
            }
            if(style.getUnderlineFont() != null) {
                font.setUnderline(style.getUnderlineFont());
            }
            if(style.getStrikeoutFont() != null) {
                font.setStrikeout(style.getStrikeoutFont());
            }
            cellStyle.setFont(font);
        }
    }

    private void cellApplyColorStyles(Style style, XSSFCellStyle cellStyle) {
        if(style.getForegroundColor() != null) {
            cellStyle.setFillForegroundColor(new XSSFColor(style.getForegroundColor()));
            cellStyle.setFillPattern(style.getFillPattern());
        }

        if(style.getBorderColor() != null) {
            cellStyle.setBorderColor(XSSFCellBorder.BorderSide.TOP, new XSSFColor(style.getBorderColor()));
            cellStyle.setBorderColor(XSSFCellBorder.BorderSide.RIGHT, new XSSFColor(style.getBorderColor()));
            cellStyle.setBorderColor(XSSFCellBorder.BorderSide.BOTTOM, new XSSFColor(style.getBorderColor()));
            cellStyle.setBorderColor(XSSFCellBorder.BorderSide.LEFT, new XSSFColor(style.getBorderColor()));
        }

        if(style.getLeftBorderColor() != null) {
            cellStyle.setLeftBorderColor(new XSSFColor(style.getLeftBorderColor()));
        }

        if(style.getRightBorderColor() != null) {
            cellStyle.setRightBorderColor(new XSSFColor(style.getRightBorderColor()));
        }

        if(style.getTopBorderColor() != null) {
            cellStyle.setTopBorderColor(new XSSFColor(style.getTopBorderColor()));
        }

        if(style.getBottomBorderColor() != null) {
            cellStyle.setBottomBorderColor(new XSSFColor(style.getBottomBorderColor()));
        }
    }

    private void cellApplyBorderStyles(Style style, XSSFCellStyle cellStyle) {
        if(style.getBorderBottom() != null) {
            cellStyle.setBorderBottom(style.getBorderBottom());
        }
        if(style.getBorderTop() != null) {
            cellStyle.setBorderTop(style.getBorderTop());
        }
        if(style.getBorderLeft() != null) {
            cellStyle.setBorderLeft(style.getBorderLeft());
        }
        if(style.getBorderRight() != null) {
            cellStyle.setBorderRight(style.getBorderRight());
        }
    }

    protected CellReference getCellReferenceForTargetId(Row row, String id) {
        return new CellReference(row.getCell(data.getColumnIndexForId(id), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
    }

    private void setCellComment(Sheet sheet, Cell cell, SpecialDataRowCell specialCell) {
        if(!StringUtils.EMPTY.equals(specialCell.getComment())) {
            final Drawing<?> drawingPatriarch = sheet.createDrawingPatriarch();
            final ClientAnchor clientAnchor = creationHelper.createClientAnchor();
            clientAnchor.setCol1(cell.getColumnIndex());
            clientAnchor.setCol2(cell.getColumnIndex() + specialCell.getCommentWidth());
            clientAnchor.setRow1(cell.getRowIndex());
            clientAnchor.setRow2(cell.getRowIndex() + specialCell.getCommentHeight());
            final Comment cellComment = drawingPatriarch.createCellComment(clientAnchor);
            cellComment.setString(creationHelper.createRichTextString(specialCell.getComment()));
        }
    }

    protected void setCellFormat(Cell cell, String format) {
        if(!StringUtils.isEmpty(format)){
            XSSFCellStyle cellStyle;
            if(!formatsCache.containsKey(format)){
                cellStyle = currentWorkbook.createCellStyle();
                cellStyle.cloneStyleFrom(cell.getCellStyle());
                cellStyle.setDataFormat(creationHelper.createDataFormat().getFormat(format));
                formatsCache.put(format, cellStyle);
            } else {
                cellStyle = formatsCache.get(format);
            }
            cell.setCellStyle(cellStyle);
        }
    }

    protected String replaceFormulaIndexes(Row targetRow, String value) {
        for (Map.Entry<String, Integer> entry : data.getTargetIndexes().entrySet()) {
            value = value.replaceAll(entry.getKey(), this.getCellReferenceForTargetId(targetRow, entry.getKey()).formatAsString());
        }
        return value;
    }

    protected void adjustColumns(Sheet sheet) {
        final List<Integer> autoSizedColumns = data.getAutoSizedColumns();

        loggerService.trace("Adjusting columns...", !autoSizedColumns.isEmpty());
        final Stopwatch adjustColumnsStopwatch = Stopwatch.createStarted();

        for (Integer autoSizedColumn : autoSizedColumns) {
            sheet.autoSizeColumn(autoSizedColumn + data.getConfiguration().getHorizontalOffset());
        }

        loggerService.trace("Columns adjusted. Time: " + adjustColumnsStopwatch.stop(), !autoSizedColumns.isEmpty());
    }

    protected void createSpecialRows(Sheet sheet) {
        final List<SpecialDataRow> specialRows = data.getSpecialRows();
        Integer countBottomRows = 0;
        loggerService.trace("Creating special rows...", !specialRows.isEmpty());
        final Stopwatch specialRowsStopwatch = Stopwatch.createStarted();
        for(SpecialDataRow specialRow : specialRows) {
            countBottomRows = specialRowSetRowIndex(countBottomRows, specialRow);
            for(final SpecialDataRowCell specialCell : specialRow.getCells()) {
                final ValueType valueType = specialCell.getValueType();
                Row row = WorkbookUtils.getOrCreateRow(sheet, specialRow.getRowIndex());
                final int columnIndexForTarget = data.getColumnIndexForId(specialCell.getTargetId()) + data.getConfiguration().getHorizontalOffset();
                Cell cell = WorkbookUtils.getOrCreateCell(row, columnIndexForTarget);
                createColumnsToMerge(sheet, row, columnIndexForTarget, specialCell.getColumnWidth());

                if(!Arrays.asList(ValueType.FORMULA, ValueType.COLLECTED_FORMULA_VALUE).contains(valueType)) {
                    WorkbookUtils.setCellValue(cell, specialCell.getValue());
                } else {
                    String formulaString = specialCell.getValue().toString();
                    if(ValueType.FORMULA.equals(valueType)) {
                        createSpecialFormulaCell(sheet, cell, formulaString);
                    } else {
                        createCollectedFormulaValueCell(sheet, specialCell, cell, formulaString);
                    }
                }
                setCellComment(sheet, cell, specialCell);
                setCellFormat(cell, specialCell.getFormat());
            }
            checkIfStickyRow(sheet, specialRow);
        }
        loggerService.trace("Special rows created. Time: " + specialRowsStopwatch.stop(), !specialRows.isEmpty());
    }

    private void createCollectedFormulaValueCell(Sheet sheet, SpecialDataRowCell specialCell, Cell cell, String formulaString) {
        Map<String, List<Integer>> valuesById = (Map<String, List<Integer>>) specialCell.getValuesById();
        if(valuesById != null) {
            for(final Map.Entry<String, List<Integer>> entry : valuesById.entrySet()) {
                String id = entry.getKey();
                List<Integer> rowIndexes = entry.getValue();
                List<String> cellReferences = new ArrayList<>();
                for(final Integer rowIndex : rowIndexes) {
                    CellReference cellReference = this.getCellReferenceForTargetId(
                        WorkbookUtils.getOrCreateRow(sheet, data.getDataRealStartRow() + rowIndex),
                        specialCell.getTargetId()
                    );
                    cellReferences.add(cellReference.formatAsString() + ":" + cellReference.formatAsString());
                }
                String joinedReferences = String.join(",", cellReferences);
                formulaString = formulaString.replaceAll(id, joinedReferences);
                cell.setCellFormula(formulaString);
            }
        }
    }

    private void checkIfStickyRow(Sheet sheet, SpecialDataRow specialRow) {
        if(specialRow.isStickyRow()) {
            sheet.createFreezePane(0, specialRow.getRowIndex() + 1, 0, specialRow.getRowIndex() + 1);
        }
    }

    private Integer specialRowSetRowIndex(Integer countBottomRows, SpecialDataRow specialRow) {
        if(specialRow.getRowIndex() == Integer.MAX_VALUE) {
            specialRow.setRowIndex(
                data.getConfiguration().getVerticalOffset() +
                    data.getDataStartRow() +
                    data.getRowsCount() +
                    countBottomRows++
            );
        } else {
            specialRow.setRowIndex(specialRow.getRowIndex() + data.getConfiguration().getVerticalOffset());
        }
        return countBottomRows;
    }

    private void createSpecialFormulaCell(Sheet sheet, Cell cell, String formulaString) {
        if(sheet.getLastRowNum() >= data.getDataStartRow()) {
            for (Map.Entry<String, Integer> entry : data.getTargetIndexes().entrySet()) {
                CellReference firstCellReference = this.getCellReferenceForTargetId(
                    WorkbookUtils.getOrCreateRow(sheet, data.getDataRealStartRow()),
                    entry.getKey()
                );
                CellReference lastCellReference = this.getCellReferenceForTargetId(
                    WorkbookUtils.getOrCreateRow(sheet, data.getDataRealStartRow() + data.getRowsCount() - 1),
                    entry.getKey()
                );
                formulaString = formulaString.replaceAll(entry.getKey(), firstCellReference.formatAsString() + ":" + lastCellReference.formatAsString());
            }
            cell.setCellFormula(formulaString);
        }
    }

    protected void createColumnsToMerge(final Sheet sheet, final Row row, final int cellIndex, final int columnWidth) {
        if(columnWidth > 1) {
            for (int i = 1; i < columnWidth; i++) {
                WorkbookUtils.getOrCreateCell(row, cellIndex + i);
            }
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), cellIndex, cellIndex + columnWidth - 1));
        }
    }
}
