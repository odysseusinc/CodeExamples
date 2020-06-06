package org.ohdsi.webapi.test;

import com.google.gson.annotations.SerializedName;

public class CohortSqlTranslate {

    @SerializedName("targetdialect")
    private String targetDialect;

    @SerializedName("SQL")
    private String sql;

    public String getTargetDialect() {

        return targetDialect;
    }

    public void setTargetDialect(String targetDialect) {

        this.targetDialect = targetDialect;
    }

    public String getSql() {

        return sql;
    }

    public void setSql(String sql) {

        this.sql = sql;
    }
}
