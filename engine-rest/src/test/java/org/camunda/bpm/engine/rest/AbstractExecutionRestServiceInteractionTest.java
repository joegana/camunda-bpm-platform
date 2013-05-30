package org.camunda.bpm.engine.rest;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.RuntimeServiceImpl;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.exception.RestException;
import org.camunda.bpm.engine.rest.helper.EqualsList;
import org.camunda.bpm.engine.rest.helper.EqualsMap;
import org.camunda.bpm.engine.rest.helper.MockProvider;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.ExecutionQuery;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.http.ContentType;

public abstract class AbstractExecutionRestServiceInteractionTest extends AbstractRestServiceTest {

  protected static final String EXECUTION_URL = TEST_RESOURCE_ROOT_PATH + "/execution/{id}";
  protected static final String SIGNAL_EXECUTION_URL = EXECUTION_URL + "/signal";
  protected static final String EXECUTION_VARIABLES_URL = EXECUTION_URL + "/variables";
  protected static final String SINGLE_EXECUTION_VARIABLE_URL = EXECUTION_VARIABLES_URL + "/{varId}";
  protected static final String MESSAGE_SUBSCRIPTION_URL = EXECUTION_URL + "/messageSubscriptions/{messageName}/trigger";
  
  private RuntimeServiceImpl runtimeServiceMock;
  
  @Before
  public void setUpRuntimeData() {
    runtimeServiceMock = mock(RuntimeServiceImpl.class);
    when(runtimeServiceMock.getVariablesLocal(MockProvider.EXAMPLE_EXECUTION_ID)).thenReturn(EXAMPLE_VARIABLES);

    when(processEngine.getRuntimeService()).thenReturn(runtimeServiceMock);
  }
  
  @Test
  public void testGetSingleExecution() {
    Execution mockExecution = MockProvider.createMockExecution();
    ExecutionQuery sampleExecutionQuery = mock(ExecutionQuery.class);
    when(runtimeServiceMock.createExecutionQuery()).thenReturn(sampleExecutionQuery);
    when(sampleExecutionQuery.executionId(MockProvider.EXAMPLE_EXECUTION_ID)).thenReturn(sampleExecutionQuery);
    when(sampleExecutionQuery.singleResult()).thenReturn(mockExecution);
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID)
      .then().expect().statusCode(Status.OK.getStatusCode())
      .body("id", equalTo(MockProvider.EXAMPLE_EXECUTION_ID))
      .body("ended", equalTo(MockProvider.EXAMPLE_EXECUTION_IS_ENDED))
      .body("processInstanceId", equalTo(MockProvider.EXAMPLE_PROCESS_INSTANCE_ID))
      .when().get(EXECUTION_URL);
  }
  
  @Test
  public void testGetNonExistingExecution() {
    ExecutionQuery sampleExecutionQuery = mock(ExecutionQuery.class);
    when(runtimeServiceMock.createExecutionQuery()).thenReturn(sampleExecutionQuery);
    when(sampleExecutionQuery.executionId(anyString())).thenReturn(sampleExecutionQuery);
    when(sampleExecutionQuery.singleResult()).thenReturn(null);
    
    String nonExistingExecutionId = "aNonExistingInstanceId";
    
    given().pathParam("id", nonExistingExecutionId)
      .then().expect().statusCode(Status.NOT_FOUND.getStatusCode()).contentType(ContentType.JSON)
      .body("type", equalTo(InvalidRequestException.class.getSimpleName()))
      .body("message", equalTo("Execution with id " + nonExistingExecutionId + " does not exist"))
      .when().get(EXECUTION_URL);
  }
  
  @Test
  public void testSignalExecution() {
    Map<String, Object> variablesJson = new HashMap<String, Object>();
    List<Map<String, Object>> variables = new ArrayList<Map<String, Object>>();
    Map<String, Object> variable = new HashMap<String, Object>();
    variable.put("name", "aKey");
    variable.put("value", 123);
    variable.put("type", "Integer");
    variables.add(variable);
    
    variablesJson.put("variables", variables);
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).contentType(ContentType.JSON).body(variablesJson)
      .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
      .when().post(SIGNAL_EXECUTION_URL);
    
    Map<String, Object> expectedSignalVariables = new HashMap<String, Object>();
    expectedSignalVariables.put("aKey", 123);
    
    verify(runtimeServiceMock).signal(eq(MockProvider.EXAMPLE_EXECUTION_ID), argThat(new EqualsMap(expectedSignalVariables)));
  }
  
  @Test
  public void testSignalNonExistingExecution() {
    doThrow(new ProcessEngineException("expected exception")).when(runtimeServiceMock).signal(anyString(), any(Map.class));
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
      .then().expect().statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode()).contentType(ContentType.JSON)
      .body("type", equalTo(RestException.class.getSimpleName()))
      .body("message", equalTo("Cannot signal execution " + MockProvider.EXAMPLE_EXECUTION_ID + ": expected exception"))
      .when().post(SIGNAL_EXECUTION_URL);
  }
  

  @Test
  public void testGetVariables() {
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID)
      .then().expect().statusCode(Status.OK.getStatusCode())
      .body("variables.size()", is(1))
      .body("variables[0].name", equalTo(EXAMPLE_VARIABLE_KEY))
      .body("variables[0].value", equalTo(EXAMPLE_VARIABLE_VALUE))
      .body("variables[0].type", equalTo(String.class.getSimpleName()))
      .when().get(EXECUTION_VARIABLES_URL);
  }
  
  @Test
  public void testGetVariablesForNonExistingExecution() {
    when(runtimeServiceMock.getVariablesLocal(anyString())).thenThrow(new ProcessEngineException("expected exception"));
    
    given().pathParam("id", "aNonExistingExecutionId")
      .then().expect().statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode()).contentType(ContentType.JSON)
      .body("type", equalTo(ProcessEngineException.class.getSimpleName()))
      .body("message", equalTo("expected exception"))
      .when().get(EXECUTION_VARIABLES_URL);
  }
  
  @Test
  public void testVariableModification() {
    Map<String, Object> messageBodyJson = new HashMap<String, Object>();
    
    List<Map<String, Object>> modifications = new ArrayList<Map<String, Object>>();
    Map<String, Object> variable = new HashMap<String, Object>();
    variable.put("name", "aKey");
    variable.put("value", 123);
    variable.put("type", "Integer");
    modifications.add(variable);
    
    messageBodyJson.put("modifications", modifications);
    
    List<String> deletions = new ArrayList<String>();
    deletions.add("deleteKey");
    messageBodyJson.put("deletions", deletions);
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).contentType(ContentType.JSON).body(messageBodyJson)
      .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
      .when().post(EXECUTION_VARIABLES_URL);
    
    Map<String, Object> expectedModifications = new HashMap<String, Object>();
    expectedModifications.put("aKey", 123);
    verify(runtimeServiceMock).updateVariablesLocal(eq(MockProvider.EXAMPLE_EXECUTION_ID), argThat(new EqualsMap(expectedModifications)), 
        argThat(new EqualsList(deletions)));
  }
  
  // TODO how can this be green?
  @Test
  public void testVariableModificationForNonExistingExecution() {
    doThrow(new ProcessEngineException("expected exception")).when(runtimeServiceMock).updateVariablesLocal(anyString(), any(Map.class), any(List.class));
    
    Map<String, Object> messageBodyJson = new HashMap<String, Object>();
    
    List<Map<String, Object>> modifications = new ArrayList<Map<String, Object>>();
    Map<String, Object> variable = new HashMap<String, Object>();
    variable.put("name", "aKey");
    variable.put("value", 123);
    variable.put("type", "Integer");
    
    messageBodyJson.put("modifications", modifications);
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).contentType(ContentType.JSON).body(messageBodyJson)
      .then().expect().statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode()).contentType(ContentType.JSON)
      .body("type", equalTo(RestException.class.getSimpleName()))
      .body("message", equalTo("Cannot modify variables for execution " + MockProvider.EXAMPLE_EXECUTION_ID + ": expected exception"))
      .when().post(EXECUTION_VARIABLES_URL);
  }
  
  @Test
  public void testEmptyVariableModification() {
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
      .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
      .when().post(EXECUTION_VARIABLES_URL);
  }
  
  @Test
  public void testGetSingleVariable() {
    String variableKey = "aVariableKey";
    int variableValue = 123;
    
    when(runtimeServiceMock.getVariableLocal(eq(MockProvider.EXAMPLE_EXECUTION_ID), eq(variableKey))).thenReturn(variableValue);
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("varId", variableKey)
      .then().expect().statusCode(Status.OK.getStatusCode())
      .body("value", is(123))
      .body("name", is(variableKey))
      .body("type", is("Integer"))
      .when().get(SINGLE_EXECUTION_VARIABLE_URL);
  }
  
  @Test
  public void testNonExistingVariable() {
    String variableKey = "aVariableKey";
    
    when(runtimeServiceMock.getVariableLocal(eq(MockProvider.EXAMPLE_EXECUTION_ID), eq(variableKey))).thenReturn(null);
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("varId", variableKey)
      .then().expect().statusCode(Status.NOT_FOUND.getStatusCode())
      .body("type", is(InvalidRequestException.class.getSimpleName()))
      .body("message", is("Execution variable with name " + variableKey + " does not exist or is null"))
      .when().get(SINGLE_EXECUTION_VARIABLE_URL);
  }
  
  @Test
  public void testGetVariableForNonExistingExecution() {
    String variableKey = "aVariableKey";
    
    when(runtimeServiceMock.getVariableLocal(eq(MockProvider.EXAMPLE_EXECUTION_ID), eq(variableKey)))
      .thenThrow(new ProcessEngineException("expected exception"));
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("varId", variableKey)
      .then().expect().statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode())
      .body("type", is(RestException.class.getSimpleName()))
      .body("message", is("Cannot get execution variable " + variableKey + ": expected exception"))
      .when().get(SINGLE_EXECUTION_VARIABLE_URL);
  }
  
  @Test
  public void testPutSingleVariable() {
    String variableKey = "aVariableKey";
    String variableValue = "aVariableValue";
    
    Map<String, Object> variableJson = new HashMap<String, Object>();
    variableJson.put("value", variableValue);
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("varId", variableKey)
      .contentType(ContentType.JSON).body(variableJson)
      .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
      .when().put(SINGLE_EXECUTION_VARIABLE_URL);
    
    verify(runtimeServiceMock).setVariableLocal(eq(MockProvider.EXAMPLE_EXECUTION_ID), eq(variableKey), 
        eq(variableValue));
  }
  
  @Test
  public void testPutSingleVariableWithNoValue() {
    String variableKey = "aVariableKey";
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("varId", variableKey)
      .contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
      .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
      .when().put(SINGLE_EXECUTION_VARIABLE_URL);
    
    verify(runtimeServiceMock).setVariableLocal(eq(MockProvider.EXAMPLE_EXECUTION_ID), eq(variableKey), 
        isNull());
  }
  
  @Test
  public void testPutVariableForNonExistingExecution() {
    String variableKey = "aVariableKey";
    String variableValue = "aVariableValue";
    
    Map<String, Object> variableJson = new HashMap<String, Object>();
    variableJson.put("value", variableValue);
    
    doThrow(new ProcessEngineException("expected exception"))
      .when(runtimeServiceMock).setVariableLocal(eq(MockProvider.EXAMPLE_EXECUTION_ID), eq(variableKey), eq(variableValue));
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("varId", variableKey)
      .contentType(ContentType.JSON).body(variableJson)
      .then().expect().statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode())
      .body("type", is(RestException.class.getSimpleName()))
      .body("message", is("Cannot put execution variable " + variableKey + ": expected exception"))
      .when().put(SINGLE_EXECUTION_VARIABLE_URL);
  }
  
  @Test
  public void testDeleteSingleVariable() {
    String variableKey = "aVariableKey";
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("varId", variableKey)
      .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
      .when().delete(SINGLE_EXECUTION_VARIABLE_URL);
    
    verify(runtimeServiceMock).removeVariableLocal(eq(MockProvider.EXAMPLE_EXECUTION_ID), eq(variableKey));
  }
  
  @Test
  public void testDeleteVariableForNonExistingExecution() {
    String variableKey = "aVariableKey";
    
    doThrow(new ProcessEngineException("expected exception"))
      .when(runtimeServiceMock).removeVariableLocal(eq(MockProvider.EXAMPLE_EXECUTION_ID), eq(variableKey));
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("varId", variableKey)
      .then().expect().statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode())
      .body("type", is(RestException.class.getSimpleName()))
      .body("message", is("Cannot delete execution variable " + variableKey + ": expected exception"))
      .when().delete(SINGLE_EXECUTION_VARIABLE_URL);
  }
  
  @Test
  public void testMessageEventTriggering() {
    String messageName = "aMessageName";
    String variableKey1 = "aVarName";
    String variableValue1 = "aVarValue";
    String variableKey2 = "anotherVarName";
    String variableValue2 = "anotherVarValue";
    
    List<Map<String, Object>> variables = new ArrayList<Map<String, Object>>();
    Map<String, Object> variable1 = new HashMap<String, Object>();
    variable1.put("name", variableKey1);
    variable1.put("value", variableValue1);
    variables.add(variable1);
    
    Map<String, Object> variable2 = new HashMap<String, Object>();
    variable2.put("name", variableKey2);
    variable2.put("value", variableValue2);
    variables.add(variable2);
    
    Map<String, Object> variablesJson = new HashMap<String, Object>();
    variablesJson.put("variables", variables);
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("messageName", messageName)
      .contentType(ContentType.JSON).body(variablesJson)
      .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
      .when().post(MESSAGE_SUBSCRIPTION_URL);
  
    Map<String, Object> expectedVariables = new HashMap<String, Object>();
    expectedVariables.put(variableKey1, variableValue1);
    expectedVariables.put(variableKey2, variableValue2);
    
    verify(runtimeServiceMock).messageEventReceived(eq(messageName), eq(MockProvider.EXAMPLE_EXECUTION_ID),
        argThat(new EqualsMap(expectedVariables)));
  }
  
  @Test
  public void testMessageEventTriggeringWithoutVariables() {
    String messageName = "aMessageName";
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("messageName", messageName)
      .contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
      .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
      .when().post(MESSAGE_SUBSCRIPTION_URL);
  
    verify(runtimeServiceMock).messageEventReceived(eq(messageName), eq(MockProvider.EXAMPLE_EXECUTION_ID),
        argThat(new EqualsMap(null)));
  }
  
  @Test
  public void testFailingMessageEventTriggering() {
    String messageName = "someMessage";
    doThrow(new ProcessEngineException("expected exception"))
      .when(runtimeServiceMock).messageEventReceived(anyString(), anyString(), any(Map.class));
    
    given().pathParam("id", MockProvider.EXAMPLE_EXECUTION_ID).pathParam("messageName", messageName)
      .contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
      .then().expect().statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode())
      .body("type", is(RestException.class.getSimpleName()))
      .body("message", is("Cannot trigger message " + messageName + " for execution " + MockProvider.EXAMPLE_EXECUTION_ID + ": expected exception"))
      .when().post(MESSAGE_SUBSCRIPTION_URL);
  }
}
