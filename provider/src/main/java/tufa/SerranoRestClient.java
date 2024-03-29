package tufa;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import alien4cloud.rest.utils.ResponseUtil;
import org.apache.commons.collections4.MapUtils;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BestMatchSpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SerranoRestClient {

    private String applicationUrl;

    private CloseableHttpClient httpClient;

    private BasicCookieStore cookieStore;

    private CookieSpec cookieSpec;

    public SerranoRestClient(String applicationUrl) {
        this.applicationUrl = applicationUrl;
        this.cookieStore = new BasicCookieStore();
        this.cookieSpec = new BestMatchSpec(null, true);
        httpClient = HttpClients.custom().setDefaultCookieStore(cookieStore).build();
    }

    /**
     * Get the cookie header from the current cookie store
     *
     * @return
     */
    public Header getCookieHeader() {
        List<Header> headers = this.cookieSpec.formatCookies(this.cookieStore.getCookies());
        return headers.get(0);
    }

    public void clearCookies() {
        this.cookieStore.clear();
    }

    public String postMultipart(String path, String fileName, InputStream data) throws IOException {
        log.debug("Send post multipart request to [" + path + "], file [" + fileName + "]");
        HttpPost httpPost = new HttpPost(applicationUrl + path);
        MultipartEntityBuilder mpBuilder = MultipartEntityBuilder.create();
        mpBuilder.addPart("file", new InputStreamBody(data, fileName));
        httpPost.setEntity(mpBuilder.build());
        CloseableHttpResponse response = httpClient.execute(httpPost);
        return ResponseUtil.toString(response);
    }

    public String postMultipart(String path, String fileName, InputStream data, Map<String, String> otherStringParts) throws IOException {
        log.debug("Send post multipart request to [" + path + "], file [" + fileName + "], with json data: " + otherStringParts);
        HttpPost httpPost = new HttpPost(applicationUrl + path);
        MultipartEntityBuilder mpBuilder = MultipartEntityBuilder.create();
        mpBuilder.addPart("file", new InputStreamBody(data, fileName));
        // mpBuilder.addPart("", contentBody);
        if (MapUtils.isNotEmpty(otherStringParts)) {
            for (Entry<String, String> entry : otherStringParts.entrySet()) {
                mpBuilder.addTextBody(entry.getKey(), entry.getValue());
            }
        }
        httpPost.setEntity(mpBuilder.build());
        CloseableHttpResponse response = httpClient.execute(httpPost);
        return ResponseUtil.toString(response);
    }

    public CloseableHttpResponse get(String path) throws IOException {
        log.debug("Send get request to [" + path + "]");
        HttpGet httpGet = new HttpGet(applicationUrl + path);
        CloseableHttpResponse response = httpClient.execute(httpGet);
        return response;
    }

    public InputStream getAsStream(String path) throws IOException {
        HttpGet httpGet = new HttpGet(applicationUrl + path);
        CloseableHttpResponse response = httpClient.execute(httpGet);
        if (response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() > 300) {
            throw new IOException("Unable to get the http resource, received non OK return code " + response.getStatusLine());
        }
        return response.getEntity().getContent();
    }

    public CloseableHttpResponse getUrlEncoded(String path, List<NameValuePair> nvps) throws IOException, URISyntaxException {
        log.debug("Send get url encoded request to [" + path + "], params [" + nvps + "]");
        URI uri = new URIBuilder(applicationUrl + path).setParameters(nvps).build();
        HttpGet httpGet = new HttpGet(uri);
        CloseableHttpResponse response = httpClient.execute(httpGet);
        return response;
    }

    public String put(String path) throws IOException {
        log.debug("Send put request to [" + path + "]");
        HttpPut httpPut = new HttpPut(applicationUrl + path);
        CloseableHttpResponse response = httpClient.execute(httpPut);
        return ResponseUtil.toString(response);
    }

    public String postUrlEncoded(String path, List<NameValuePair> npvs) throws IOException {
        log.debug("Send post url encoded request to [" + path + "], params [" + npvs + "]");
        HttpPost httpPost = new HttpPost(applicationUrl + path);
        httpPost.setEntity(new UrlEncodedFormEntity(npvs));
        CloseableHttpResponse response = httpClient.execute(httpPost);
        return ResponseUtil.toString(response);
    }

    public CloseableHttpResponse postJSon(String path, String jSon) throws IOException {
        log.debug("Send post json request to [" + path + "], jSon [" + jSon + "]");
        HttpPost httpPost = new HttpPost(applicationUrl + path);
        if (jSon != null) {
            StringEntity jsonInput = new StringEntity(jSon);
            jsonInput.setContentType("application/json");
            httpPost.setEntity(jsonInput);
        }
        CloseableHttpResponse response = httpClient.execute(httpPost);
        return response;
    }

    public CloseableHttpResponse post(String path) throws IOException {
        return postJSon(path, null);
    }

    public String putJSon(String path, String jSon) throws IOException {
        log.debug("Send put json request to [" + path + "], jSon [" + jSon + "]");
        HttpPut httpPut = new HttpPut(applicationUrl + path);
        StringEntity jsonInput = new StringEntity(jSon);
        jsonInput.setContentType("application/json");
        httpPut.setEntity(jsonInput);
        CloseableHttpResponse response = httpClient.execute(httpPut);
        return ResponseUtil.toString(response);
    }

    public String patch(String path) throws IOException {
        log.debug("Send patch request to [" + path + "]");
        HttpPatch httpPatch = new HttpPatch(applicationUrl + path);
        CloseableHttpResponse response = httpClient.execute(httpPatch);
        return ResponseUtil.toString(response);
    }

    public String patchJSon(String path, String jSon) throws IOException {
        log.debug("Send patch json request to [" + path + "], jSon [" + jSon + "]");
        HttpPatch httpPatch = new HttpPatch(applicationUrl + path);
        StringEntity jsonInput = new StringEntity(jSon);
        jsonInput.setContentType("application/json");
        httpPatch.setEntity(jsonInput);
        CloseableHttpResponse response = httpClient.execute(httpPatch);
        return ResponseUtil.toString(response);
    }

    public String putUrlEncoded(String path, List<NameValuePair> npvs) throws IOException {
        log.debug("Send put url encoded request to [" + path + "], params [" + npvs + "]");
        HttpPut httpPut = new HttpPut(applicationUrl + path);
        httpPut.setEntity(new UrlEncodedFormEntity(npvs));
        CloseableHttpResponse response = httpClient.execute(httpPut);
        return ResponseUtil.toString(response);
    }

    public String delete(String path) throws IOException {
        log.debug("Send delete request to [" + path + "]");
        org.apache.http.client.methods.HttpDelete httpDelete = new org.apache.http.client.methods.HttpDelete(applicationUrl + path);
        CloseableHttpResponse response = httpClient.execute(httpDelete);
        return ResponseUtil.toString(response);
    }

    public void close() throws IOException {
        log.debug("Close client");
        this.httpClient.close();
    }

    public Boolean isApplicationUrlAvailable() throws IOException {
        log.debug("Send head request to [" + applicationUrl + "]");
        HttpHead httpHead = new HttpHead(applicationUrl);
        CloseableHttpResponse response = httpClient.execute(httpHead);
        log.debug("response status of head request to [" + applicationUrl + "] is: " + response.getStatusLine().getStatusCode());
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
    }

}
