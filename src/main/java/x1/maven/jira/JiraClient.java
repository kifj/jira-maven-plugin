package x1.maven.jira;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public class JiraClient {
  private final Gson gson = new GsonBuilder().create();
  private String baseApiUrl;
  private String jiraUserName;
  private String jiraPassword;
  private Log log;

  public JiraClient(URL jiraServer, String jiraUserName, String jiraPassword, Log log) {
    this.jiraUserName = jiraUserName;
    this.jiraPassword = jiraPassword;
    this.baseApiUrl = jiraServer.toString() + "/rest/api/2";
    this.log = log;
  }

  public void addWatcher(String issueKey, String body) throws Exception {
    URI uri = new URIBuilder(baseApiUrl + "/issue/" + issueKey + "/watchers").build();
    HttpClientContext context = createHttpContext(uri);

    HttpPost request = new HttpPost(uri);
    request.setHeader("Content-Type", "application/json");
    request.setHeader("Accept", "application/json");
    request.setEntity(new ByteArrayEntity(body.getBytes("UTF-8")));

    CloseableHttpClient httpClient = createHttpClient();
    log.debug("Execute " + request);
    HttpResponse response = httpClient.execute(request, context);
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      EntityUtils.consumeQuietly(entity);
    }
    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_NO_CONTENT) {
      log.warn("addWatcher failed with status " + response.getStatusLine().getStatusCode());
    }
  }

  public void addLink(String body) throws Exception {
    URI uri = new URIBuilder(baseApiUrl + "/issueLink").build();
    HttpClientContext context = createHttpContext(uri);

    HttpPost request = new HttpPost(uri);
    request.setHeader("Content-Type", "application/json");
    request.setHeader("Accept", "application/json");
    request.setEntity(new ByteArrayEntity(body.getBytes("UTF-8")));

    CloseableHttpClient httpClient = createHttpClient();
    log.debug("Execute " + request);
    HttpResponse response = httpClient.execute(request, context);
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      EntityUtils.consumeQuietly(entity);
    }
    if (response.getStatusLine().getStatusCode() < HttpStatus.SC_CREATED) {
      log.warn("addLink failed with status " + response.getStatusLine().getStatusCode());
    }
  }

  public CreateResult createIssue(String body) throws Exception {
    URI uri = new URIBuilder(baseApiUrl + "/issue").build();
    HttpClientContext context = createHttpContext(uri);

    HttpPost request = new HttpPost(uri);
    request.setHeader("Content-Type", "application/json");
    request.setHeader("Accept", "application/json");
    request.setEntity(new ByteArrayEntity(body.getBytes("UTF-8")));

    log.debug("Execute " + request);
    CloseableHttpClient httpClient = createHttpClient();
    HttpResponse response = httpClient.execute(request, context);
    CreateResult json = extractJsonResponse(response, HttpStatus.SC_CREATED);
    return json;
  }

  private HttpClientContext createHttpContext(URI uri) {
    if (jiraUserName != null && jiraPassword != null) {
      UsernamePasswordCredentials creds = new UsernamePasswordCredentials(jiraUserName, jiraPassword);
      CredentialsProvider credsProvider = new BasicCredentialsProvider();
      HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
      credsProvider.setCredentials(new AuthScope(host), creds);
      AuthCache authCache = new BasicAuthCache();
      BasicScheme basicAuth = new BasicScheme();
      authCache.put(host, basicAuth);

      HttpClientContext context = HttpClientContext.create();
      context.setCredentialsProvider(credsProvider);
      context.setAuthCache(authCache);
      return context;
    } else {
      return HttpClientContext.create();
    }
  }

  private CloseableHttpClient createHttpClient() {
    RequestConfig requestConfig = RequestConfig.custom().build();
    CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
    return httpClient;
  }

  private CreateResult extractJsonResponse(HttpResponse response, int expectedHttpStatus) throws IOException {
    StatusLine statusLine = response.getStatusLine();
    int statusCode = statusLine.getStatusCode();
    String json = EntityUtils.toString(response.getEntity());
    if (expectedHttpStatus != statusCode) {
      throw new IOException("Failed with status " + response.getStatusLine().getStatusCode() + "\n" + json);
    }
    return parseResult(json);
  }

  private CreateResult parseResult(String json) throws IOException {
    CreateResult result;
    try {
      result = gson.fromJson(json, CreateResult.class);
    } catch (JsonParseException e) {
      throw new IOException("Error parsing response: " + e.getMessage());
    }
    return result;
  }
}
