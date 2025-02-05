package iudx.aaa.server.token;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.policy.PolicyServiceImpl;

/**
 * Mocks, stubs the PolicyService.
 * Implements the verifyPolicy method.
 */
public class MockPolicyFactory {

  private static PolicyService policyService;
  AsyncResult<JsonObject> asyncResult;

  @SuppressWarnings("unchecked")
  public MockPolicyFactory() {
    if (policyService == null) {
      policyService = Mockito.mock(PolicyServiceImpl.class);
    }

    asyncResult = Mockito.mock(AsyncResult.class);
    Mockito.doAnswer((Answer<AsyncResult<JsonObject>>) arguments -> {
      ((Handler<AsyncResult<JsonObject>>) arguments.getArgument(1)).handle(asyncResult);
      return null;
    }).when(policyService).verifyPolicy(Mockito.any(), Mockito.any());
  }

  public PolicyService getInstance() {
    return MockPolicyFactory.policyService;
  }

  /**
   * Response for PolicyService mock.
   * 
   * @param status
   */
  public void setResponse(String status) {
    JsonObject response = new JsonObject();
    if ("valid".equals(status)) {
      response.put("status", "success");
      Mockito.when(asyncResult.result()).thenReturn(response);
      Mockito.when(asyncResult.failed()).thenReturn(false);
      Mockito.when(asyncResult.succeeded()).thenReturn(true);
    } else {
      response.put("status", "failed");
      Mockito.when(asyncResult.cause()).thenReturn(new Throwable(response.toString()));
      Mockito.when(asyncResult.succeeded()).thenReturn(false);
      Mockito.when(asyncResult.failed()).thenReturn(true);
    }
  }

  /**
   * Mock success response of verifyPolicy
   * 
   * @param status if it is a valid/invalid call
   * @param item the cat ID of the item
   * @param url the url of the server
   */
  public void setResponse(String status, String item, String url) {
    if ("valid".equals(status)) {
    JsonObject response = new JsonObject();
      response.put("status", "success");
      response.put("cat_id", item);
      response.put("url", url);
      response.put("constraints", new JsonObject().put("access", new JsonArray().add("api")));
      Mockito.when(asyncResult.result()).thenReturn(response);
      Mockito.when(asyncResult.failed()).thenReturn(false);
      Mockito.when(asyncResult.succeeded()).thenReturn(true);
    }
  }
}
