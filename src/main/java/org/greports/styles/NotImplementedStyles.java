package org.greports.styles;

import org.greports.styles.interfaces.StyledReport;
import org.greports.styles.stylesbuilders.ReportStylesBuilder;

import java.util.HashMap;
import java.util.Map;

public final class NotImplementedStyles implements StyledReport {

    @Override
    public Map<String, ReportStylesBuilder> getReportStyles(Integer rowsCount) {
        return new HashMap<>();
    }
}