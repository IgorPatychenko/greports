package engine;

import content.ReportData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class ReportDataInjector {

    protected final XSSFWorkbook currentWorkbook;
    protected final ReportData reportData;
    protected Map<String, XSSFCellStyle> _formatsCache = new HashMap<>();
    protected CreationHelper creationHelper;

    protected ReportDataInjector(XSSFWorkbook currentWorkbook, ReportData reportData) {
        this.currentWorkbook = currentWorkbook;
        this.reportData = reportData;
        this.creationHelper = this.currentWorkbook.getCreationHelper();
    }

    protected void setCellFormat(Cell cell, String format) {
        if(format != null && !format.isEmpty()){
            XSSFCellStyle cellStyle;
            if(!_formatsCache.containsKey(format)){
                cellStyle = currentWorkbook.createCellStyle();
                cellStyle.cloneStyleFrom(cell.getCellStyle());
                cellStyle.setDataFormat(creationHelper.createDataFormat().getFormat(format));
                _formatsCache.put(format, cellStyle);
            } else {
                cellStyle = _formatsCache.get(format);
            }
            cell.setCellStyle(cellStyle);
        }
    }

    protected void setCellValue(Cell cell, Object value) {
        if(value instanceof Date){
            cell.setCellValue(((Date) value));
        } else if(value instanceof Number){
            cell.setCellValue(((Number) value).doubleValue());
        } else if(value instanceof Boolean){
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(Objects.toString(value, ""));
        }
    }

    protected CellReference getCellReferenceForTargetId(Row row, String id) {
        return new CellReference(row.getCell(reportData.getColumnIndexForTarget(id)));
    }

    protected abstract void inject();
    protected abstract void injectData(Sheet sheet);

}
