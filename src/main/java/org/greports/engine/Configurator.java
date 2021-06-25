package org.greports.engine;

import org.apache.commons.lang3.StringUtils;
import org.greports.utils.Utils;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Configurator implements Serializable {

    private static final long serialVersionUID = 396420494885876888L;
    private final transient GreportsGenerator greportsGenerator;

    private Map<Integer, String> overriddenTitles = new HashMap<>();
    private String sheetName;
    private final Map<Class<?>, String> formats = new HashMap<>();
    private URL templateUrl;
    private List<Integer> removedColumns = new ArrayList<>();
    private List<Integer> autosizedColumns;

    protected Configurator(final GreportsGenerator greportsGenerator) {
        this.greportsGenerator = greportsGenerator;
    }

    protected GreportsGenerator getReportGenerator() {
        return greportsGenerator;
    }

    public Map<Integer, String> getOverriddenTitles() {
        return overriddenTitles;
    }

    public List<Integer> getRemovedColumns() {
        return this.removedColumns;
    }

    public List<Integer> getAutosizedColumns() {
        return autosizedColumns;
    }

    public Configurator setOverriddenTitles(final Map<Integer, String> overriddenTitles) {
        Utils.validateNotNull(overriddenTitles);
        this.overriddenTitles = overriddenTitles;
        return this;
    }

    public Configurator setRemovedColumns(final List<Integer> removedColumns) {
        Utils.validateNotNull(removedColumns);
        this.removedColumns = removedColumns;
        return this;
    }

    public Configurator setAutosizedColumns(List<Integer> autosizedColumns) {
        Utils.validateNotNull(autosizedColumns);
        this.autosizedColumns = autosizedColumns;
        return this;
    }

    public Configurator overrideTitle(final Integer columnIndex, final String title) {
        Utils.validateNotNull(columnIndex);
        Utils.validateNotNull(title);
        this.overriddenTitles.put(columnIndex, title);
        return this;
    }

    public Configurator setSheetName(final String newName) {
        Utils.validateNotNull(newName);
        this.sheetName = newName;
        return this;
    }

    public String getSheetName() {
        return sheetName;
    }

    public Configurator setTemplateUrl(final URL templateUrl) {
        Utils.validateNotNull(templateUrl);
        this.templateUrl = templateUrl;
        return this;
    }

    public URL getTemplateUrl() {
        return templateUrl;
    }

    public Configurator setFormatForClass(final Class<?> clazz, final String format) {
        Utils.validateNotNull(clazz);
        Utils.validateNotNull(format);
        this.formats.put(clazz, format);
        return this;
    }

    public String getFormatForClass(final Class<?> clazz, final String defaultFormat) {
        Utils.validateNotNull(clazz);
        if(StringUtils.isEmpty(defaultFormat)) {
            return this.formats.getOrDefault(clazz, StringUtils.EMPTY);
        }
        return defaultFormat;
    }
}
