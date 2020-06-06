package org.ohdsi.webapi.test;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GCPLogin {
    private static final String BASE_URL = "http://192.168.35.87:8080/";
    //        private static final String BASE_URL = "https://atlas-gcp.odysseusinc.com/";
    private static final String COHORT_DEF_URL = BASE_URL + "WebAPI/cohortdefinition/%s";
    private static final String COHORT_SQL_URL = BASE_URL + "WebAPI/cohortdefinition/sql";
    private static final String SQL_TRANSLATE_URL = BASE_URL + "WebAPI/sqlrender/translate";
    private static final int COHORT_DEF_ID = 2;
    private static final String CREDENTIAL_JSON = "/application_default_credentials.json";
    private static final List<String> DIALECTS = new ArrayList<String>() {{
        add("postgresql");
        add("sql server");
        add("oracle");
        add("redshift");
        add("bigquery");
        add("impala");
        add("pdw");
        add("netezza");
    }};

    private CloseableHttpClient httpClient;
    private AccessToken token;
    private Gson gson;

    public GCPLogin() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
        this.httpClient = HttpClients.createDefault();
        this.gson = new Gson();
    }

    public static void main(String... args) throws Exception {
        GCPLogin gcpLogin = new GCPLogin();
        gcpLogin.process();
    }

    public void process() throws IOException {
        this.token = getAccessToken();
        Cohort cohort = getCohort(COHORT_DEF_ID);
        CohortSQL cohortSQL = getCohortSQL(cohort);
        Map<String, TranslatedStatement> translations = getSqlTranslate(cohortSQL);
        translations.forEach((k, v) -> System.out.println(k + "::" + v.getTargetSQL()));
    }

    private Cohort getCohort(int cohortDefId) throws IOException {
        String cohortDefUrl = getCohortDefinitionServiceUrl(cohortDefId);
        String cohortStr = executeRequest(cohortDefUrl);

        return entityFromJson(cohortStr, Cohort.class);
    }

    private CohortSQL getCohortSQL(Cohort cohort) throws IOException {
        String expression = cohort.getExpression();
        String json = "{\"expression\":" + expression + "}";
        String cohortSqlStr = executeRequest(COHORT_SQL_URL, "POST", json);

        return entityFromJson(cohortSqlStr, CohortSQL.class);
    }

    private Map<String, TranslatedStatement> getSqlTranslate(CohortSQL cohortSQL) throws IOException {
        Map<String, TranslatedStatement> translations = new HashMap<>();
        DIALECTS.forEach(d -> {
            CohortSqlTranslate sqlTranslate = new CohortSqlTranslate();
            sqlTranslate.setTargetDialect(d);
            sqlTranslate.setSql(cohortSQL.getTemplateSql());
            try {
                String cohortSqlTranslate = executeRequest(SQL_TRANSLATE_URL, "POST", entityToJson(sqlTranslate));
                TranslatedStatement statement = entityFromJson(cohortSqlTranslate, TranslatedStatement.class);
                translations.put(d, statement);
            } catch (IOException e) {
                throw new RuntimeException("Error processing request", e);
            }
        });
        return translations;
    }

    private String executeRequest(String url) throws IOException {
        return executeRequest(url, "GET", null);
    }

    private String executeRequest(String url, String method, String json) throws IOException {
        HttpRequestBase httpRequest;
        if ("GET".equals(method)) {
            httpRequest = new HttpGet(url);
        } else {
            httpRequest = new HttpPost(url);
            StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
            ((HttpPost) httpRequest).setEntity(entity);
        }
        httpRequest.addHeader("Authorization", "Bearer " + this.token.getTokenValue());
        CloseableHttpResponse response = this.httpClient.execute(httpRequest);
        try {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    return EntityUtils.toString(entity);
                } else {
                    throw new RuntimeException("Entity must not be null");
                }
            } else {
                throw new RuntimeException("Error processing request: " + response.getStatusLine());
            }
        } finally {
            response.close();
        }
    }

    private AccessToken getAccessToken() throws IOException {
        InputStream in = GCPLogin.class.getResourceAsStream(CREDENTIAL_JSON);
        GoogleCredentials credentials = GoogleCredentials.fromStream(in);
        if (credentials.getAccessToken() == null) {
            credentials.refresh();
        }
        return credentials.getAccessToken();
    }

    private String getCohortDefinitionServiceUrl(int cohortDefId) {
        return String.format(COHORT_DEF_URL, cohortDefId);
    }

    private <T> T entityFromJson(String json, Class<T> clazz) {
        JsonObject object = (JsonObject) JsonParser.parseString(json);
        return this.gson.fromJson(object, clazz);
    }

    private String entityToJson(Object entity) {
        return this.gson.toJson(entity);
    }
}
