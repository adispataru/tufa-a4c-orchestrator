package tufa.tufas.util;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HttpUtil {

    private void sleepWhenErrorHappen(long before, long timeout) {
        if (System.currentTimeMillis() - before > timeout) {
            Assert.fail("Test timeout");
        }
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e1) {
        }
    }

    public void checkUrl(String url, String containingText, long timeout) {
        log.info("Checking url {}", url);
        long before = System.currentTimeMillis();
        CloseableHttpClient httpClient = HttpClients.custom().build();
        while (true) {
            try {
                HttpGet httpGet = new HttpGet(url);
                CloseableHttpResponse response = httpClient.execute(httpGet);
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Status code " + response.getStatusLine().getStatusCode());
                    }
                    if (response.getStatusLine().getStatusCode() == 404) {
                        sleepWhenErrorHappen(before, timeout);
                        continue;
                    }
                    Assert.assertTrue(response.getStatusLine().getStatusCode() >= 200 && response.getStatusLine().getStatusCode() < 300);
                    String responseText = EntityUtils.toString(response.getEntity());
                    if (log.isDebugEnabled()) {
                        log.debug(responseText);
                    }
                    if (StringUtils.isNotBlank(containingText)) {
                        if (!responseText.contains(containingText)) {
                            log.info("Expect to receive \n {} but received \n {}", containingText, responseText);
                            sleepWhenErrorHappen(before, timeout);
                            continue;
                        }
                    }
                    return;
                } finally {
                    response.close();
                }
            } catch (IOException e) {
                sleepWhenErrorHappen(before, timeout);
            }
        }
    }
}
