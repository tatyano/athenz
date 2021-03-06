/*
 * Copyright 2016 Yahoo Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.athenz.zts.store;

import static org.testng.Assert.*;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.yahoo.athenz.zts.AWSTemporaryCredentials;
import com.yahoo.athenz.zts.ResourceException;
import com.yahoo.athenz.zts.ZTSConsts;
import com.yahoo.athenz.zts.store.CloudStore;

public class CloudStoreTest {

    public final static String AWS_INSTANCE_DOCUMENT = "{\n"
            + "  \"devpayProductCodes\" : null,\n"
            + "  \"availabilityZone\" : \"us-west-2a\",\n"
            + "  \"privateIp\" : \"10.10.10.10\",\n"
            + "  \"version\" : \"2010-08-31\",\n"
            + "  \"instanceId\" : \"i-056921225f1fbb47a\",\n"
            + "  \"billingProducts\" : null,\n"
            + "  \"instanceType\" : \"t2.micro\",\n"
            + "  \"accountId\" : \"111111111111\",\n"
            + "  \"pendingTime\" : \"2016-04-26T05:37:23Z\",\n"
            + "  \"imageId\" : \"ami-c229c0a2\",\n"
            + "  \"architecture\" : \"x86_64\",\n"
            + "  \"kernelId\" : null,\n"
            + "  \"ramdiskId\" : null,\n"
            + "  \"region\" : \"us-west-2\"\n"
            + "}";
    
    public final static String AWS_IAM_ROLE_INFO = "{\n"
            + "\"Code\" : \"Success\",\n"
            + "\"LastUpdated\" : \"2016-04-26T05:37:04Z\",\n"
            + "\"InstanceProfileArn\" : \"arn:aws:iam::111111111111:instance-profile/athenz.zts,athenz\",\n"
            + "\"InstanceProfileId\" : \"AIPAJAVNLUGEWFWTIDPRA\"\n"
            + "}";
    
    @Test
    public void testGetS3ClientNullCreds() {
        CloudStore store = new CloudStore(null);
        store.awsEnabled = true;
        store.credentials = null;
        try {
            store.getS3Client();
            fail();
        } catch (ResourceException ex) {
            assertEquals(500, ex.getCode());
        }
        store.close();
    }
    
    @Test
    public void testGetS3ClientAWSNotEnabled() {
        CloudStore store = new CloudStore(null);
        store.credentials = null;
        try {
            store.getS3Client();
            fail();
        } catch (ResourceException ex) {
            assertEquals(500, ex.getCode());
        }
        store.close();
    }
    
    @Test
    public void testGetS3Client() {
        
        System.setProperty(ZTSConsts.ZTS_PROP_AWS_PUBLIC_CERT, "src/test/resources/aws_public.crt");
        CloudStore store = new CloudStore(null);
        store.credentials = new BasicSessionCredentials("accessKey", "secretKey", "token");
        store.awsEnabled = true;
        assertNotNull(store.getS3Client());
        
        store.awsRegion = "us-west-2";
        assertNotNull(store.getS3Client());
        store.close();
    }
    
    @Test
    public void testGetTokenServiceClient() {
        CloudStore store = new CloudStore(null);
        store.credentials = new BasicSessionCredentials("accessKey", "secretKey", "token");
        store.awsEnabled = true;
        assertNotNull(store.getTokenServiceClient());
        store.close();
    }
    
    @Test
    public void testUpdateAccountUpdate() {
        
        CloudStore store = new CloudStore(null);
        assertNull(store.getCloudAccount("iaas"));
        
        // set the account to 1234
        
        store.updateAccount("iaas", "1234");
        assertEquals("1234", store.getCloudAccount("iaas"));
        
        // update the account value
        
        store.updateAccount("iaas", "1235");
        assertEquals("1235", store.getCloudAccount("iaas"));
        store.close();
    }
    
    @Test
    public void testUpdateAccountDelete() {
        
        CloudStore store = new CloudStore(null);
        
        // set the account to 1234
        
        store.updateAccount("iaas", "1234");
        assertEquals("1234", store.getCloudAccount("iaas"));
        
        // delete the account with null
        
        store.updateAccount("iaas", null);
        assertNull(store.getCloudAccount("iaas"));

        // update the account value
        
        store.updateAccount("iaas", "1235");
        assertEquals("1235", store.getCloudAccount("iaas"));
        
        // delete the account with empty string
        
        store.updateAccount("iaas", "");
        assertNull(store.getCloudAccount("iaas"));
        store.close();
    }
    
    @Test
    public void testGetAssumeRoleRequest() {
        
        CloudStore store = new CloudStore(null);
        AssumeRoleRequest req = store.getAssumeRoleRequest("1234", "admin", "sys.auth.zts");
        assertEquals("arn:aws:iam::1234:role/admin", req.getRoleArn());
        assertEquals("sys.auth.zts", req.getRoleSessionName());
        store.close();
    }
    
    @Test
    public void testParseInstanceInfo() {
        CloudStore store = new CloudStore(null);
        assertTrue(store.parseInstanceInfo(AWS_INSTANCE_DOCUMENT));
        assertEquals(store.awsRegion, "us-west-2");
        store.close();
    }
    
    @Test
    public void testParseInstanceInfoInvalid() {
        
        CloudStore store = new CloudStore(null);
        assertFalse(store.parseInstanceInfo("some_invalid_doc"));
        store.close();
    }
    
    @Test
    public void testParseInstanceInfoRegion() {
        
        // first this should fail since we have no region
        // override and the document has no region
        
        CloudStore store = new CloudStore(null);
        assertFalse(store.parseInstanceInfo("{\"accountId\":\"012345678901\"}"));
        
        // now we're going to use the same doc with override
        
        System.setProperty(ZTSConsts.ZTS_PROP_AWS_REGION_NAME, "us-west-3");
        store.close();

        store = new CloudStore(null);
        assertTrue(store.parseInstanceInfo("{\"accountId\":\"012345678901\"}"));
        assertEquals(store.awsRegion, "us-west-3");
        System.clearProperty(ZTSConsts.ZTS_PROP_AWS_REGION_NAME);
        store.close();
    }

    @Test
    public void testParseIamRoleInfoInvalid() {
        
        CloudStore store = new CloudStore(null);
        assertFalse(store.parseIamRoleInfo("some_invalid_doc"));
        store.close();
    }
    
    @Test
    public void testParseIamRoleInfoMissingInstanceProfile() {
        
        CloudStore store = new CloudStore(null);
        assertFalse(store.parseIamRoleInfo("{\"accountId\":\"012345678901\"}"));
        assertFalse(store.parseIamRoleInfo("{\"accountId\":\"012345678901\",\"InstanceProfileArn\":\"\"}"));
        store.close();
    }
    
    @Test
    public void testParseIamRoleInfoInvalidInstanceProfile() {
        
        CloudStore store = new CloudStore(null);
        assertFalse(store.parseIamRoleInfo("{\"accountId\":\"012345678901\"}"));
        assertFalse(store.parseIamRoleInfo("{\"accountId\":\"012345678901\",\"InstanceProfileArn\":\"invalid\"}"));
        store.close();
    }
    
    @Test
    public void testParseIamRoleInfo() {
        CloudStore store = new CloudStore(null);
        assertTrue(store.parseIamRoleInfo(AWS_IAM_ROLE_INFO));
        assertEquals(store.awsRole, "athenz.zts");
        store.close();
    }
    
    @Test
    public void testParseInstanceProfileArn() {
        
        CloudStore store = new CloudStore(null);
        assertTrue(store.parseInstanceProfileArn("arn:aws:iam::111111111111:instance-profile/athenz.zts,athenz"));
        assertEquals(store.awsRole, "athenz.zts");
        store.close();
    }
    
    @Test
    public void testParseInstanceProfileArnInvalidPrefix() {
        
        CloudStore store = new CloudStore(null);
        
        // invalid starting prefix
        
        assertFalse(store.parseInstanceProfileArn("arn:aws:iam:111111111111:instance-profile/athenz.zts,athenz"));
        assertFalse(store.parseInstanceProfileArn("arn:aws:iam2:111111111111:instance-profile/athenz.zts,athenz"));
        assertFalse(store.parseInstanceProfileArn("instance-profile/athenz.zts,athenz"));
        store.close();
    }
    
    @Test
    public void testParseInstanceProfileArnInvalidProfile() {
        
        CloudStore store = new CloudStore(null);
        
        // missing instance-profile part
        
        assertFalse(store.parseInstanceProfileArn("arn:aws:iam::111111111111:instance-profile2/athenz.zts,athenz"));
        assertFalse(store.parseInstanceProfileArn("arn:aws:iam::111111111111:instance/athenz.zts,athenz"));
        store.close();
    }
    
    @Test
    public void testParseInstanceProfileArnInvalidNoProfile() {
        
        CloudStore store = new CloudStore(null);
        
        // no profile name
        
        assertFalse(store.parseInstanceProfileArn("arn:aws:iam::111111111111:instance-profile/"));
        store.close();
    }
    
    @Test
    public void testParseInstanceProfileArnCloud() {
        
        CloudStore store = new CloudStore(null);
        // cloud name is optional for backwards compatibility
        assertTrue(store.parseInstanceProfileArn("arn:aws:iam::111111111111:instance-profile/athenz.zts"));
        assertEquals(store.awsRole, "athenz.zts");
        assertTrue(store.parseInstanceProfileArn("arn:aws:iam::111111111111:instance-profile/athenz.proxy,athenz,test"));
        assertEquals(store.awsRole, "athenz.proxy");
        store.close();
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testGetMetaDataExceptions() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/exc1")).thenThrow(InterruptedException.class);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/exc2")).thenThrow(ExecutionException.class);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/exc3")).thenThrow(TimeoutException.class);
        
        assertNull(store.getMetaData("/exc1"));
        assertNull(store.getMetaData("/exc2"));
        assertNull(store.getMetaData("/exc3"));
        store.close();
    }
    
    @Test
    public void testGetMetaDataFailureStatus() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse response = Mockito.mock(ContentResponse.class);
        Mockito.when(response.getStatus()).thenReturn(404);
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/iam-info")).thenReturn(response);

        assertNull(store.getMetaData("/iam-info"));
        store.close();
    }
    
    @Test
    public void testGetMetaDataNullResponse() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse response = Mockito.mock(ContentResponse.class);
        Mockito.when(response.getStatus()).thenReturn(200);
        Mockito.when(response.getContentAsString()).thenReturn(null);
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/iam-info")).thenReturn(response);

        assertNull(store.getMetaData("/iam-info"));
        store.close();
    }
    
    @Test
    public void testGetMetaDataEmptyResponse() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse response = Mockito.mock(ContentResponse.class);
        Mockito.when(response.getStatus()).thenReturn(200);
        Mockito.when(response.getContentAsString()).thenReturn("");
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/iam-info")).thenReturn(response);

        assertNull(store.getMetaData("/iam-info"));
        store.close();
    }
    
    @Test
    public void testGetMetaDataValidResponse() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse response = Mockito.mock(ContentResponse.class);
        Mockito.when(response.getStatus()).thenReturn(200);
        Mockito.when(response.getContentAsString()).thenReturn("json-document");
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/iam-info")).thenReturn(response);

        assertEquals(store.getMetaData("/iam-info"), "json-document");
        store.close();
    }
    
    @Test
    public void testLoadBootMetaDataInvalidDocumentGet() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse response = Mockito.mock(ContentResponse.class);
        Mockito.when(response.getStatus()).thenReturn(404);
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(response);
        
        assertFalse(store.loadBootMetaData());
        store.close();
    }
    
    @Test
    public void testLoadBootMetaDataInvalidDocumentParse() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse response = Mockito.mock(ContentResponse.class);
        Mockito.when(response.getStatus()).thenReturn(200);
        Mockito.when(response.getContentAsString()).thenReturn("{\"accountId\":\"012345678901\"}");
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(response);
        
        assertFalse(store.loadBootMetaData());
        store.close();
    }
    
    @Test
    public void testLoadBootMetaDataInvalidDocumentException() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse response = Mockito.mock(ContentResponse.class);
        Mockito.when(response.getStatus()).thenReturn(200);
        Mockito.when(response.getContentAsString()).thenReturn("json-document");
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(response);
        
        assertFalse(store.loadBootMetaData());
        store.close();
    }
    
    @Test
    public void testLoadBootMetaDataInvalidSignature() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse responseDoc = Mockito.mock(ContentResponse.class);
        Mockito.when(responseDoc.getStatus()).thenReturn(200);
        Mockito.when(responseDoc.getContentAsString()).thenReturn(AWS_INSTANCE_DOCUMENT);
        
        ContentResponse responseSig = Mockito.mock(ContentResponse.class);
        Mockito.when(responseSig.getStatus()).thenReturn(404);
        
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(responseDoc);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")).thenReturn(responseSig);

        assertFalse(store.loadBootMetaData());
        store.close();
    }
    
    @Test
    public void testLoadBootMetaDataInvalidIamInfoGet() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        
        ContentResponse responseDoc = Mockito.mock(ContentResponse.class);
        Mockito.when(responseDoc.getStatus()).thenReturn(200);
        Mockito.when(responseDoc.getContentAsString()).thenReturn(AWS_INSTANCE_DOCUMENT);
        
        ContentResponse responseSig = Mockito.mock(ContentResponse.class);
        Mockito.when(responseSig.getStatus()).thenReturn(200);
        Mockito.when(responseSig.getContentAsString()).thenReturn("pkcs7-signature");
        
        ContentResponse responseInfo = Mockito.mock(ContentResponse.class);
        Mockito.when(responseInfo.getStatus()).thenReturn(404);
        
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(responseDoc);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")).thenReturn(responseSig);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/meta-data/iam/info")).thenReturn(responseInfo);

        assertFalse(store.loadBootMetaData());
        store.close();
    }
    
    @Test
    public void testLoadBootMetaDataInvalidIamInfoException() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        
        ContentResponse responseDoc = Mockito.mock(ContentResponse.class);
        Mockito.when(responseDoc.getStatus()).thenReturn(200);
        Mockito.when(responseDoc.getContentAsString()).thenReturn(AWS_INSTANCE_DOCUMENT);
        
        ContentResponse responseSig = Mockito.mock(ContentResponse.class);
        Mockito.when(responseSig.getStatus()).thenReturn(200);
        Mockito.when(responseSig.getContentAsString()).thenReturn("pkcs7-signature");
        
        ContentResponse responseInfo = Mockito.mock(ContentResponse.class);
        Mockito.when(responseInfo.getStatus()).thenReturn(200);
        Mockito.when(responseInfo.getContentAsString()).thenReturn("invalid-info");
        
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(responseDoc);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")).thenReturn(responseSig);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/meta-data/iam/info")).thenReturn(responseInfo);

        assertFalse(store.loadBootMetaData());
        store.close();
    }
    
    @Test
    public void testLoadBootMetaDataInvalidIamInfoParse() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        
        ContentResponse responseDoc = Mockito.mock(ContentResponse.class);
        Mockito.when(responseDoc.getStatus()).thenReturn(200);
        Mockito.when(responseDoc.getContentAsString()).thenReturn(AWS_INSTANCE_DOCUMENT);
        
        ContentResponse responseSig = Mockito.mock(ContentResponse.class);
        Mockito.when(responseSig.getStatus()).thenReturn(200);
        Mockito.when(responseSig.getContentAsString()).thenReturn("pkcs7-signature");
        
        ContentResponse responseInfo = Mockito.mock(ContentResponse.class);
        Mockito.when(responseInfo.getStatus()).thenReturn(200);
        Mockito.when(responseInfo.getContentAsString()).thenReturn("{\"accountId\":\"012345678901\",\"InstanceProfileArn\":\"invalid\"}");
        
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(responseDoc);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")).thenReturn(responseSig);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/meta-data/iam/info")).thenReturn(responseInfo);

        assertFalse(store.loadBootMetaData());
        store.close();
    }
    
    @Test
    public void testLoadBootMetaData() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        
        ContentResponse responseDoc = Mockito.mock(ContentResponse.class);
        Mockito.when(responseDoc.getStatus()).thenReturn(200);
        Mockito.when(responseDoc.getContentAsString()).thenReturn(AWS_INSTANCE_DOCUMENT);
        
        ContentResponse responseSig = Mockito.mock(ContentResponse.class);
        Mockito.when(responseSig.getStatus()).thenReturn(200);
        Mockito.when(responseSig.getContentAsString()).thenReturn("pkcs7-signature");
        
        ContentResponse responseInfo = Mockito.mock(ContentResponse.class);
        Mockito.when(responseInfo.getStatus()).thenReturn(200);
        Mockito.when(responseInfo.getContentAsString()).thenReturn(AWS_IAM_ROLE_INFO);
        
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(responseDoc);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")).thenReturn(responseSig);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/meta-data/iam/info")).thenReturn(responseInfo);

        assertTrue(store.loadBootMetaData());
        assertEquals(store.awsRole, "athenz.zts");
        assertEquals(store.awsRegion, "us-west-2");
        store.close();
    }
    
    @Test
    public void testFetchRoleCredentialsNoRole() {
        
        CloudStore store = new CloudStore(null);
        
        store.awsRole = null;
        assertFalse(store.fetchRoleCredentials());
        
        store.awsRole = "";
        assertFalse(store.fetchRoleCredentials());
        store.close();
    }
    
    @Test
    public void testFetchRoleCredentialsNoCreds() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        store.awsRole = "athenz.zts";
        
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse response = Mockito.mock(ContentResponse.class);
        Mockito.when(response.getStatus()).thenReturn(404);
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/meta-data/iam/security-credentials/athenz.zts")).thenReturn(response);
        
        assertFalse(store.fetchRoleCredentials());
        store.close();
    }
    
    @Test
    public void testFetchRoleCredentialInvalidCreds() throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        store.awsRole = "athenz.zts";
        
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ContentResponse response = Mockito.mock(ContentResponse.class);
        Mockito.when(response.getStatus()).thenReturn(200);
        Mockito.when(response.getContentAsString()).thenReturn("invalid-creds");

        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/meta-data/iam/security-credentials/athenz.zts")).thenReturn(response);
        
        assertFalse(store.fetchRoleCredentials());
        store.close();
    }
    
    @Test
    public void testInitializeAwsSupportInvalidDocument()  throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        
        ContentResponse responseDoc = Mockito.mock(ContentResponse.class);
        Mockito.when(responseDoc.getStatus()).thenReturn(200);
        Mockito.when(responseDoc.getContentAsString()).thenReturn("invalid-document");
        
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(responseDoc);

        try {
            store.awsEnabled = true;
            store.initializeAwsSupport();
            fail();
        } catch (ResourceException ex) {
            assertEquals(ex.getCode(), 500);
        }
        store.close();
    }
    
    @Test
    public void testInitializeAwsSupportInvalidCreds()  throws InterruptedException, ExecutionException, TimeoutException {
        
        CloudStore store = new CloudStore(null);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        
        ContentResponse responseDoc = Mockito.mock(ContentResponse.class);
        Mockito.when(responseDoc.getStatus()).thenReturn(200);
        Mockito.when(responseDoc.getContentAsString()).thenReturn(AWS_INSTANCE_DOCUMENT);
        
        ContentResponse responseSig = Mockito.mock(ContentResponse.class);
        Mockito.when(responseSig.getStatus()).thenReturn(200);
        Mockito.when(responseSig.getContentAsString()).thenReturn("pkcs7-signature");
        
        ContentResponse responseInfo = Mockito.mock(ContentResponse.class);
        Mockito.when(responseInfo.getStatus()).thenReturn(200);
        Mockito.when(responseInfo.getContentAsString()).thenReturn(AWS_IAM_ROLE_INFO);
        
        ContentResponse responseCreds = Mockito.mock(ContentResponse.class);
        Mockito.when(responseCreds.getStatus()).thenReturn(200);
        Mockito.when(responseCreds.getContentAsString()).thenReturn("invalid-creds");
        
        store.setHttpClient(httpClient);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/document")).thenReturn(responseDoc);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/dynamic/instance-identity/pkcs7")).thenReturn(responseSig);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/meta-data/iam/info")).thenReturn(responseInfo);
        Mockito.when(httpClient.GET("http://169.254.169.254/latest/meta-data/iam/security-credentials/athenz.zts")).thenReturn(responseCreds);

        try {
            store.awsEnabled = true;
            store.initializeAwsSupport();
            fail();
        } catch (ResourceException ex) {
            assertEquals(ex.getCode(), 500);
        }
        store.close();
    }
    
    @Test
    public void testAssumeAWSRoleAWSNotEnabled() {
        CloudStore cloudStore = new CloudStore(null);
        try {
            cloudStore.assumeAWSRole("account", "sycner", "athenz.syncer");
            fail();
        } catch (ResourceException ex) {
            assertEquals(ex.getCode(), 500);
        }
        cloudStore.close();
    }
    
    @Test
    public void testAssumeAWSRole() {
        MockCloudStore cloudStore = new MockCloudStore();
        cloudStore.awsEnabled = true;
        AssumeRoleResult mockResult = Mockito.mock(AssumeRoleResult.class);
        Credentials creds = Mockito.mock(Credentials.class);
        Mockito.when(creds.getAccessKeyId()).thenReturn("accesskeyid");
        Mockito.when(creds.getSecretAccessKey()).thenReturn("secretaccesskey");
        Mockito.when(creds.getSessionToken()).thenReturn("sessiontoken");
        Mockito.when(creds.getExpiration()).thenReturn(new Date());
        Mockito.when(mockResult.getCredentials()).thenReturn(creds);
        cloudStore.setAssumeRoleResult(mockResult);
        cloudStore.setAssumeAWSRole(true);

        AWSTemporaryCredentials awsCreds = cloudStore.assumeAWSRole("account", "syncer", "athenz.syncer");
        assertNotNull(awsCreds);
        assertEquals(awsCreds.getAccessKeyId(), "accesskeyid");
        assertEquals(awsCreds.getSessionToken(), "sessiontoken");
        assertEquals(awsCreds.getSecretAccessKey(), "secretaccesskey");
        cloudStore.close();
    }
    
    @Test
    public void testGetSshKeyReqType() {
        CloudStore cloudStore = new CloudStore(null);
        final String req = "{\"principals\":[\"localhost\"],\"pubkey\":\"ssh-rsa AAAs\"" +
                ",\"reqip\":\"10.10.10.10\",\"requser\":\"user\",\"certtype\":\"host\",\"transid\":\"0\"}";
        assertEquals(cloudStore.getSshKeyReqType(req), "host");
        
        final String req2 = "{\"principals\":[\"localhost\"],\"pubkey\":\"ssh-rsa AAAs\"" +
                ",\"reqip\":\"10.10.10.10\",\"requser\":\"user\",\"certtype2\":\"host\",\"transid\":\"0\"}";
        assertNull(cloudStore.getSshKeyReqType(req2));
        
        final String req3 = "{invalid-json";
        assertNull(cloudStore.getSshKeyReqType(req3));
        cloudStore.close();
    }
}
