package engine;

import annotations.Report;
import annotations.Configuration;
import annotations.LoaderColumn;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import utils.AnnotationUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class ReportLoader {

    private String reportName;
    private Workbook currentWorkbook;

    public ReportLoader(String reportName, String filePath) throws IOException, InvalidFormatException {
        this(reportName, new File(filePath));
    }

    public ReportLoader(String reportName, File file) throws IOException, InvalidFormatException {
        this(reportName, new FileInputStream(file));
    }

    public ReportLoader(String reportName, InputStream inputStream) throws IOException, InvalidFormatException {
        this.reportName = reportName;
        this.currentWorkbook = WorkbookFactory.create(inputStream);
    }

    public <T> List<T> bindForClass(Class<T> clazz) {
        final Report reportAnnotation = AnnotationUtils.getReportAnnotation(clazz);
        final Configuration configuration = AnnotationUtils.getReportConfiguration(reportAnnotation, reportName);
        final List<AbstractMap.SimpleEntry<Method, LoaderColumn>> simpleEntries = reportLoaderMethodsWithColumnAnnotations(clazz);
        return this.loadData(configuration, simpleEntries, clazz);
    }

    private <T> List<T> loadData(Configuration configuration, List<AbstractMap.SimpleEntry<Method, LoaderColumn>> simpleEntries, Class<T> clazz) {
        List<T> data = new ArrayList<>();
        Sheet sheet = currentWorkbook.getSheet(configuration.sheetName());
        for(int i = configuration.dataOffset(); i <= sheet.getLastRowNum(); i++) {
            try {
                final T instance = clazz.newInstance();
                final Row row = sheet.getRow(i);
                for(int cellIndex = row.getFirstCellNum(), methodIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++, methodIndex++) {
                    final Cell cell = row.getCell(cellIndex);
                    final Method method = simpleEntries.get(methodIndex).getKey();
                    instanceSetValueFromCell(method, instance, cell);
                }
                data.add(instance);
            } catch(InstantiationException e) {
                throw new RuntimeException("Cannot create new instance of @" + clazz.getSimpleName() + " class. Needs to have public constructor without parameters");
            } catch (InvocationTargetException | IllegalAccessException ignored) {}
        }
        return data;
    }

    private <T> void instanceSetValueFromCell(final Method method, final T instance, final Cell cell) throws InvocationTargetException, IllegalAccessException {
        if(CellType.BOOLEAN.equals(cell.getCellTypeEnum())){
            method.invoke(instance, cell.getBooleanCellValue());
        } else if(CellType.STRING.equals(cell.getCellTypeEnum())){
            method.invoke(instance, cell.getRichStringCellValue().getString());
        } else if(CellType.NUMERIC.equals(cell.getCellTypeEnum())){
            if (DateUtil.isCellDateFormatted(cell)) {
                method.invoke(instance, cell.getDateCellValue());
            } else {
                final Class<?> parameterType = method.getParameterTypes()[0];
                if(parameterType.equals(Double.class) || parameterType.getName().equals("double")){
                    method.invoke(instance, cell.getNumericCellValue());
                } else if(parameterType.equals(Integer.class) || parameterType.getName().equals("int")) {
                    method.invoke(instance, new Double(cell.getNumericCellValue()).intValue());
                } else if(parameterType.equals(Long.class) || parameterType.getName().equals("long")){
                    method.invoke(instance, new Double(cell.getNumericCellValue()).longValue());
                } else if(parameterType.equals(Float.class) || parameterType.getName().equals("float")){
                    method.invoke(instance, new Double(cell.getNumericCellValue()).floatValue());
                } else if(parameterType.equals(Short.class) || parameterType.getName().equals("short")){
                    method.invoke(instance, new Double(cell.getNumericCellValue()).shortValue());
                }
            }
        } else if(CellType.FORMULA.equals(cell.getCellTypeEnum())) {
            method.invoke(instance, cell.getCellFormula());
        } else {
            method.invoke(instance, "");
        }
    }

    private <T> List<AbstractMap.SimpleEntry<Method, LoaderColumn>> reportLoaderMethodsWithColumnAnnotations(Class<T> clazz){
        List<AbstractMap.SimpleEntry<Method, LoaderColumn>> list = new ArrayList<>();
        Function<AbstractMap.SimpleEntry<Method, LoaderColumn>, Void> columnFunction = entry -> {
            list.add(entry);
            return null;
        };
        AnnotationUtils.loaderMethodsWithColumnAnnotations(clazz, columnFunction, reportName);
        return list;
    }

}