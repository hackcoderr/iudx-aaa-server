package iudx.aaa.server.policy;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.data.Interval;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.CreateDelegationRequest;
import iudx.aaa.server.apiserver.CreatePolicyNotification;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.DeleteDelegationRequest;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.UpdatePolicyNotification;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.policy.Constants.itemTypes;
import iudx.aaa.server.policy.Constants.roles;
import iudx.aaa.server.policy.Constants.status;
import iudx.aaa.server.registration.RegistrationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_MISSING_INFO;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.CAT_ID;
import static iudx.aaa.server.policy.Constants.CHECK_ADMIN_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_DELEGATOINS_VERIFY;
import static iudx.aaa.server.policy.Constants.CHECK_POLICY;
import static iudx.aaa.server.policy.Constants.COMPOSE_FAILURE;
import static iudx.aaa.server.policy.Constants.CONSUMER_ROLE;
import static iudx.aaa.server.policy.Constants.CREATE_NOTIFI_POLICY_REQUEST;
import static iudx.aaa.server.policy.Constants.DELEGATE_ROLE;
import static iudx.aaa.server.policy.Constants.DELETE_DELEGATIONS;
import static iudx.aaa.server.policy.Constants.DELETE_FAILURE;
import static iudx.aaa.server.policy.Constants.DUPLICATE;
import static iudx.aaa.server.policy.Constants.DUP_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_DEL_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_LIST_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_DUP_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.ERR_LIST_NOTIF;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_AUTH_DELE_CREATE;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_AUTH_DELE_DELETE;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ID;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ROLES;
import static iudx.aaa.server.policy.Constants.EXPIRYTIME;
import static iudx.aaa.server.policy.Constants.GET_CONSUMER_CONSTRAINTS;
import static iudx.aaa.server.policy.Constants.GET_DELEGATIONS_BY_ID;
import static iudx.aaa.server.policy.Constants.GET_FROM_ROLES_TABLE;
import static iudx.aaa.server.policy.Constants.GET_POLICIES;
import static iudx.aaa.server.policy.Constants.GET_POLICIES_JOIN;
import static iudx.aaa.server.policy.Constants.GET_POLICIES_JOIN_DELEGATE;
import static iudx.aaa.server.policy.Constants.GET_RES_OWNER;
import static iudx.aaa.server.policy.Constants.GET_RES_SERVER_OWNER;
import static iudx.aaa.server.policy.Constants.GET_RES_SERVER_OWNER_ID;
import static iudx.aaa.server.policy.Constants.GET_RES_SER_OWNER;
import static iudx.aaa.server.policy.Constants.GET_RES_SER_OWNER_JOIN;
import static iudx.aaa.server.policy.Constants.GET_SERVER_POLICIES;
import static iudx.aaa.server.policy.Constants.GET_URL;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.ID_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.INCORRECT_ITEM_TYPE;
import static iudx.aaa.server.policy.Constants.INSERT_NOTIF_APPROVED_ID;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.INVALID_DELEGATE;
import static iudx.aaa.server.policy.Constants.INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.INVALID_TUPLE;
import static iudx.aaa.server.policy.Constants.INVALID_USER;
import static iudx.aaa.server.policy.Constants.ITEMID;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.ITEMTYPE;
import static iudx.aaa.server.policy.Constants.LIST_DELEGATE_AS_PROVIDER_DELEGATE;
import static iudx.aaa.server.policy.Constants.LIST_DELEGATE_AUTH_DELEGATE;
import static iudx.aaa.server.policy.Constants.LOG_DB_ERROR;
import static iudx.aaa.server.policy.Constants.NIL_UUID;
import static iudx.aaa.server.policy.Constants.NOT_RES_OWNER;
import static iudx.aaa.server.policy.Constants.NO_ADMIN_POLICY;
import static iudx.aaa.server.policy.Constants.NO_RES_SERVER;
import static iudx.aaa.server.policy.Constants.NO_USER;
import static iudx.aaa.server.policy.Constants.OWNERID;
import static iudx.aaa.server.policy.Constants.OWNER_DETAILS;
import static iudx.aaa.server.policy.Constants.OWNER_ID;
import static iudx.aaa.server.policy.Constants.POLICY_NOT_FOUND;
import static iudx.aaa.server.policy.Constants.PROVIDER_ROLE;
import static iudx.aaa.server.policy.Constants.REQ_ID_ALREADY_NOT_EXISTS;
import static iudx.aaa.server.policy.Constants.REQ_ID_ALREADY_PROCESSED;
import static iudx.aaa.server.policy.Constants.RES;
import static iudx.aaa.server.policy.Constants.RESOURCE_SERVER_TABLE;
import static iudx.aaa.server.policy.Constants.RES_GRP;
import static iudx.aaa.server.policy.Constants.RES_SERVER;
import static iudx.aaa.server.policy.Constants.ROLE;
import static iudx.aaa.server.policy.Constants.SELECT_CONSUM_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.SELECT_NOTIF_POLICY_REQUEST;
import static iudx.aaa.server.policy.Constants.SELECT_PROVIDER_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.SEL_NOTIF_ITEM_ID;
import static iudx.aaa.server.policy.Constants.SEL_NOTIF_POLICY_ID;
import static iudx.aaa.server.policy.Constants.SEL_NOTIF_REQ_ID;
import static iudx.aaa.server.policy.Constants.SET_INTERVALSTYLE;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.SUCCESS;
import static iudx.aaa.server.policy.Constants.SUCC_LIST_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.SUCC_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_DELETE_DELE;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_LIST_DELEGS;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_POLICY_DEL;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_POLICY_READ;
import static iudx.aaa.server.policy.Constants.SUCC_UPDATE_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.UNAUTHORIZED;
import static iudx.aaa.server.policy.Constants.UNAUTHORIZED_DELEGATE;
import static iudx.aaa.server.policy.Constants.UPDATE_NOTIF_REQ_APPROVED;
import static iudx.aaa.server.policy.Constants.UPDATE_NOTIF_REQ_REJECTED;
import static iudx.aaa.server.policy.Constants.URL;
import static iudx.aaa.server.policy.Constants.USERID;
import static iudx.aaa.server.policy.Constants.USER_DETAILS;
import static iudx.aaa.server.policy.Constants.USER_ID;
import static iudx.aaa.server.token.Constants.INVALID_POLICY;

/**
 * The Policy Service Implementation.
 *
 * <h1>Policy Service Implementation</h1>
 *
 * <p>The Policy Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.policy.PolicyService}.
 *
 * @version 1.0
 * @since 2020-12-15
 */
public class PolicyServiceImpl implements PolicyService {

  private static final Logger LOGGER = LogManager.getLogger(PolicyServiceImpl.class);
  private final PgPool pool;
  private final RegistrationService registrationService;
  private final deletePolicy deletePolicy;
  private final createPolicy createPolicy;
  private final createDelegate createDelegate;
  private final CatalogueClient catalogueClient;
  private final JsonObject authOptions;
  private final JsonObject catServerOptions;
  // Create the pooled client
  /* for converting getUserDetails's JsonObject to map */
  Function<JsonObject, Map<String, JsonObject>> jsonObjectToMap =
      (obj) -> {
        return obj.stream()
            .collect(
                Collectors.toMap(val -> (String) val.getKey(), val -> (JsonObject) val.getValue()));
      };

  public PolicyServiceImpl(
      PgPool pool,
      RegistrationService registrationService,
      CatalogueClient catalogueClient,
      JsonObject authOptions,
      JsonObject catServerOptions) {
    this.pool = pool;
    this.registrationService = registrationService;
    this.catalogueClient = catalogueClient;
    this.authOptions = authOptions;
    this.catServerOptions = catServerOptions;
    this.deletePolicy = new deletePolicy(pool, authOptions);
    this.createPolicy = new createPolicy(pool, authOptions);
    this.createDelegate = new createDelegate(pool, authOptions);
  }

  @Override
  public PolicyService createPolicy(
      List<CreatePolicyRequest> request, User user,
      JsonObject data, Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    List<Roles> roles = user.getRoles();
      boolean isDelegate = !data.isEmpty();
      String providerId;
      if(isDelegate)
          providerId = data.getString("providerId");
      else
          providerId = user.getUserId();
    // check duplicate
    List<CreatePolicyRequest> duplicates =
        request.stream()
            .collect(
                Collectors.groupingBy(
                    p -> p.getUserId() + "-" + p.getItemId() + "-" + p.getItemType(),
                    Collectors.toList()))
            .values()
            .stream()
            .filter(i -> i.size() > 1)
            .flatMap(j -> j.stream())
            .collect(Collectors.toList());

    if (duplicates.size() > 0) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(DUPLICATE)
              .detail(duplicates.get(0).toString())
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    if (!roles.contains(Roles.ADMIN)
        && !roles.contains(Roles.PROVIDER)
        && !roles.contains(Roles.DELEGATE)) {

      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(INVALID_ROLE)
              .detail(INVALID_ROLE)
              .status(403)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Set<UUID> users =
        request.stream().map(e -> UUID.fromString(e.getUserId())).collect(Collectors.toSet());

    Future<Set<UUID>> UserExist = createPolicy.checkUserExist(users);

    List<String> exp =
        request.stream()
            .filter(tagObject -> !tagObject.getExpiryTime().isEmpty())
            .map(CreatePolicyRequest::getExpiryTime)
            .collect(Collectors.toList());

    Future<Void> validateExp;
    if (!exp.isEmpty()) validateExp = createPolicy.validateExpiry(exp);
    else {
      validateExp = Future.succeededFuture();
    }

    List<String> resServerIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject
                        .getItemType()
                        .toUpperCase()
                        .equals(itemTypes.RESOURCE_SERVER.toString()))
            .map(CreatePolicyRequest::getItemId)
            .collect(Collectors.toList());

    List<String> resGrpIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject
                        .getItemType()
                        .toUpperCase()
                        .equals(itemTypes.RESOURCE_GROUP.toString()))
            .map(CreatePolicyRequest::getItemId)
            .collect(Collectors.toList());

    // the format for resource group item id when split by '/' should be of exactly length 4
    if (!resGrpIds.stream().allMatch(itemTypeCheck -> itemTypeCheck.split("/").length == 4)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(INCORRECT_ITEM_TYPE)
              .detail(INCORRECT_ITEM_TYPE)
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<String> resIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject.getItemType().toUpperCase().equals(itemTypes.RESOURCE.toString()))
            .map(CreatePolicyRequest::getItemId)
            .collect(Collectors.toList());

    // the format for resource item id when split by '/' should be of greater than len of resource
    // group(4)
    if (!resIds.stream().allMatch(itemTypeCheck -> itemTypeCheck.split("/").length > 4)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(INCORRECT_ITEM_TYPE)
              .detail(INCORRECT_ITEM_TYPE)
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }
    Map<String, List<String>> catItem = new HashMap<>();

    if (resServerIds.size() > 0) {
      if (!roles.contains(Roles.ADMIN)) {
        Response r =
            new Response.ResponseBuilder()
                .type(URN_INVALID_ROLE)
                .title(INVALID_ROLE)
                .detail(INVALID_ROLE)
                .status(403)
                .build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return this;
      } else catItem.put(RES_SERVER, resServerIds);
    } else {
      if (!roles.contains(Roles.PROVIDER) && !roles.contains(Roles.DELEGATE)) {
        Response r =
            new Response.ResponseBuilder()
                .type(URN_INVALID_ROLE)
                .title(INVALID_ROLE)
                .detail(INVALID_ROLE)
                .status(403)
                .build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return this;
      }
      if (resGrpIds.size() > 0) catItem.put(RES_GRP, resGrpIds);
      if (resIds.size() > 0) catItem.put(RES, resIds);
    }
    Future<Map<String, ResourceObj>> reqItemDetail;
    if (catItem.containsKey(RES_SERVER)) {
      reqItemDetail = createPolicy.getResSerDetails(catItem.get(RES_SERVER), user.getUserId());
    } else reqItemDetail = catalogueClient.checkReqItems(catItem);

    Future<Boolean> ItemChecks =
        CompositeFuture.all(UserExist, validateExp, reqItemDetail)
            .compose(
                obj -> {
                  if (!UserExist.result().isEmpty()) {
                    LOGGER.debug("UserExist fail:: " + UserExist.result().toString());
                    Response r =
                        new ResponseBuilder()
                            .status(400)
                            .type(URN_INVALID_INPUT)
                            .title(INVALID_USER)
                            .detail(UserExist.result().iterator().next().toString())
                            .build();
                    return Future.failedFuture(new ComposeException(r));
                  }

                  if (catItem.containsKey(RES_SERVER)) return Future.succeededFuture(false);

                  return Future.succeededFuture(true);
                });

    Future<Boolean> checkAuthPolicy =
        ItemChecks.compose(
            obj -> {
              if (ItemChecks.result().equals(false)) return Future.succeededFuture(false);
              return createPolicy.checkAuthPolicy(user.getUserId());
            });

    Future<List<UUID>> checkDelegate =
        checkAuthPolicy.compose(
            checkAut -> {
              if (checkAut.equals(false)) return Future.succeededFuture(new ArrayList<>());
              List<ResourceObj> resourceObj = new ArrayList<>(reqItemDetail.result().values());

              List<UUID> owners =
                  resourceObj.stream()
                      .distinct()
                      .map(ResourceObj::getOwnerId)
                      .collect(Collectors.toList());

              List<UUID> owned =
                  owners.stream()
                      .filter(x -> !x.equals(UUID.fromString(providerId)))
                      .collect(Collectors.toList());

              if (owned.isEmpty()){
                  return Future.succeededFuture(new ArrayList<>());
              }
              else{
                  Response r =
                          new Response.ResponseBuilder()
                                  .type(URN_INVALID_INPUT)
                                  .title(UNAUTHORIZED)
                                  .detail(UNAUTHORIZED)
                                  .status(403)
                                  .build();
                  return Future.failedFuture(new ComposeException(r));
                  //return createPolicy.checkDelegate(owned, user.getUserId());
              }
            });

    // use only reqItemDetail to insert items
    Future<Boolean> insertPolicy =
        checkDelegate.compose(
            succ -> createPolicy.insertPolicy(request, reqItemDetail.result(), user));

    insertPolicy
        .onSuccess(
            succ -> {
              Response r =
                  new Response.ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title("added policies")
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            obj -> {
              LOGGER.error(obj.getMessage());
              if (obj instanceof ComposeException) {
                ComposeException e = (ComposeException) obj;
                handler.handle(Future.succeededFuture(e.getResponse().toJson()));
              } else handler.handle(Future.failedFuture(INTERNALERROR));
            });
    return this;
  }

  @Override
  public PolicyService deletePolicy(
      JsonArray request, User user,JsonObject data, Handler<AsyncResult<JsonObject>> handler) {
      LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
      boolean isDelegate = !data.isEmpty();
      // check if all req items exist to delete;
      if (user.getUserId().equals(NIL_UUID)) {
          // empty user object
          Response r =
                  new Response.ResponseBuilder()
                          .type(URN_MISSING_INFO)
                          .title(String.valueOf(URN_MISSING_INFO))
                          .detail(NO_USER)
                          .status(401)
                          .build();
          handler.handle(Future.succeededFuture(r.toJson()));
          return this;
      }

      List<Roles> roles = user.getRoles();

      if (!roles.contains(Roles.ADMIN)
              && !roles.contains(Roles.PROVIDER)
              && !roles.contains(Roles.DELEGATE)) {
          // 403 not allowed to create policy
          Response r =
                  new Response.ResponseBuilder()
                          .type(URN_INVALID_ROLE)
                          .title(INVALID_ROLE)
                          .detail(INVALID_ROLE)
                          .status(401)
                          .build();
          handler.handle(Future.succeededFuture(r.toJson()));
          return this;
      }

      List<UUID> req =
              request.stream()
                      .map(JsonObject.class::cast)
                      .filter(tagObject -> !tagObject.getString(ID).isEmpty())
                      .map(tagObject -> UUID.fromString(tagObject.getString(ID)))
                      .collect(Collectors.toList());
      // map of policy to object with policy details
      Future<Map<UUID, JsonObject>> policyDetails = deletePolicy.checkPolicyExist(req);

     Future<Void> ownerCheck = policyDetails
              .compose(
                      checkSuc -> {
                          List<UUID> ownerIds =
                                  checkSuc.values().stream()
                                          .map(map -> UUID.fromString(map.getString(OWNERID)))
                                          .filter(x -> !x.equals(UUID.fromString(user.getUserId())))
                                          .distinct()
                                          .collect(Collectors.toList());

                          if (!ownerIds.isEmpty()) {
                              if (isDelegate) {
                                  // not resource server type
                                  List<UUID> ids =
                                          checkSuc.values().stream()
                                                  .filter(x -> !x.getString(ITEMTYPE)
                                                          .equalsIgnoreCase(RESOURCE_SERVER_TABLE.toUpperCase()))
                                                  .map(map -> UUID.fromString(map.getString(OWNERID)))
                                                  .filter(x -> !x.equals(UUID.fromString(data.getString("providerId"))))
                                                  .distinct()
                                                  .collect(Collectors.toList());
                                  if (!ids.isEmpty()) {
                                      Response r =
                                              new Response.ResponseBuilder()
                                                      .type(URN_INVALID_INPUT)
                                                      .title(ID_NOT_PRESENT)
                                                      .detail(ID_NOT_PRESENT)
                                                      .status(400)
                                                      .build();
                                      return Future.failedFuture(new ComposeException(r));
                                  }
                                  return Future.succeededFuture();
                              } else {
                                  Response r =
                                          new Response.ResponseBuilder()
                                                  .type(URN_INVALID_INPUT)
                                                  .title(ID_NOT_PRESENT)
                                                  .detail(ID_NOT_PRESENT)
                                                  .status(400)
                                                  .build();
                                  return Future.failedFuture(new ComposeException(r));
                              }
                          }
                          return Future.succeededFuture();
                      });

      ownerCheck.compose( succ ->
              deletePolicy
                      .delPolicy(req)
                      .onSuccess(
                              resp -> {
                                  Response r =
                                          new Response.ResponseBuilder()
                                                  .type(URN_SUCCESS)
                                                  .title(SUCC_TITLE_POLICY_DEL)
                                                  .status(200)
                                                  .build();
                                  handler.handle(Future.succeededFuture(r.toJson()));
                              }))
              .onFailure(
                      obj -> {
                          LOGGER.error(obj.getMessage());
                          if (obj instanceof ComposeException) {
                              ComposeException e = (ComposeException) obj;
                              handler.handle(Future.succeededFuture(e.getResponse().toJson()));
                          } else handler.handle(Future.failedFuture(INTERNALERROR));
                      });
      return this;
  }

  @Override
  public PolicyService listPolicy(
      User user, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      // empty user object
      Response r =
          new Response.ResponseBuilder()
              .type(URN_MISSING_INFO)
              .title(String.valueOf(URN_MISSING_INFO))
              .detail(NO_USER)
              .status(404)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    boolean isDelegate = false;
    JsonArray respPolicies = new JsonArray();
    Future<List<JsonObject>> getResGrpPolicy;
    Future<List<JsonObject>> getResIdPolicy;
    Future<List<JsonObject>> getResSerPolicy;

    UUID user_id;
    isDelegate = !data.isEmpty();
    if (isDelegate) {
      user_id = UUID.fromString(data.getString("providerId"));
    } else user_id = UUID.fromString(user.getUserId());

    Collector<Row, ?, List<JsonObject>> policyCollector =
        Collectors.mapping(Row::toJson, Collectors.toList());

    if (!isDelegate) {
      getResGrpPolicy =
          pool.withConnection(
                  conn ->
                      conn.preparedQuery(
                              GET_POLICIES + itemTypes.RESOURCE_GROUP + GET_POLICIES_JOIN)
                          .collecting(policyCollector)
                          .execute(Tuple.of(user_id, itemTypes.RESOURCE_GROUP, status.ACTIVE))
                          .map(SqlResult::value))
              .onFailure(
                  obj -> {
                    LOGGER.error("failed getResGrpPolicy  " + obj.getMessage());
                    handler.handle(Future.failedFuture(INTERNALERROR));
                  });

      getResIdPolicy =
          pool.withConnection(
                  conn ->
                      conn.preparedQuery(GET_POLICIES + itemTypes.RESOURCE + GET_POLICIES_JOIN)
                          .collecting(policyCollector)
                          .execute(Tuple.of(user_id, itemTypes.RESOURCE, status.ACTIVE))
                          .map(SqlResult::value))
              .onFailure(
                  obj -> {
                    LOGGER.error("failed getResIdPolicy  " + obj.getMessage());
                    handler.handle(Future.failedFuture(INTERNALERROR));
                  });

      getResSerPolicy =
          pool.withConnection(
                  conn ->
                      conn.preparedQuery(
                              GET_SERVER_POLICIES + itemTypes.RESOURCE_SERVER + GET_POLICIES_JOIN)
                          .collecting(policyCollector)
                          .execute(Tuple.of(user_id, itemTypes.RESOURCE_SERVER, status.ACTIVE))
                          .map(SqlResult::value))
              .onFailure(
                  obj -> {
                    LOGGER.error("failed getResSerPolicy  " + obj.getMessage());
                    handler.handle(Future.failedFuture(INTERNALERROR));
                  });

    } else {
      getResGrpPolicy =
          pool.withConnection(
                  conn ->
                      conn.preparedQuery(
                              GET_POLICIES + itemTypes.RESOURCE_GROUP + GET_POLICIES_JOIN_DELEGATE)
                          .collecting(policyCollector)
                          .execute(Tuple.of(user_id, itemTypes.RESOURCE_GROUP, status.ACTIVE))
                          .map(SqlResult::value))
              .onFailure(
                  obj -> {
                    LOGGER.error("failed getResGrpPolicy  " + obj.getMessage());
                    handler.handle(Future.failedFuture(INTERNALERROR));
                  });

      getResIdPolicy =
          pool.withConnection(
                  conn ->
                      conn.preparedQuery(
                              GET_POLICIES + itemTypes.RESOURCE + GET_POLICIES_JOIN_DELEGATE)
                          .collecting(policyCollector)
                          .execute(Tuple.of(user_id, itemTypes.RESOURCE, status.ACTIVE))
                          .map(SqlResult::value))
              .onFailure(
                  obj -> {
                    LOGGER.error("failed getResIdPolicy  " + obj.getMessage());
                    handler.handle(Future.failedFuture(INTERNALERROR));
                  });

      getResSerPolicy =
          pool.withConnection(
                  conn ->
                      conn.preparedQuery(
                              GET_SERVER_POLICIES
                                  + itemTypes.RESOURCE_SERVER
                                  + GET_POLICIES_JOIN_DELEGATE)
                          .collecting(policyCollector)
                          .execute(Tuple.of(user_id, itemTypes.RESOURCE_SERVER, status.ACTIVE))
                          .map(SqlResult::value))
              .onFailure(
                  obj -> {
                    LOGGER.error("failed getResSerPolicy  " + obj.getMessage());
                    handler.handle(Future.failedFuture(INTERNALERROR));
                  });
    }

    CompositeFuture.all(getResGrpPolicy, getResIdPolicy, getResSerPolicy)
        .onSuccess(
            obj -> {
              List<JsonObject> policies = new ArrayList<>();
              if (obj.list().get(0) != null) {
                policies.addAll((List<JsonObject>) obj.list().get(0));
              }

              if (obj.list().get(1) != null) {
                policies.addAll((List<JsonObject>) obj.list().get(1));
              }

              if (obj.list().get(2) != null) {
                policies.addAll((List<JsonObject>) obj.list().get(2));
              }

              policies = new ArrayList<>(new HashSet<>(policies));

              List<String> userId =
                  policies.stream()
                      .map(JsonObject.class::cast)
                      .filter(tagObject -> !tagObject.getString(USER_ID).isEmpty())
                      .map(tagObject -> tagObject.getString(USER_ID))
                      .collect(Collectors.toList());

              userId.addAll(
                  policies.stream()
                      .map(JsonObject.class::cast)
                      .filter(tagObject -> !tagObject.getString(OWNER_ID).isEmpty())
                      .map(tagObject -> tagObject.getString(OWNER_ID))
                      .collect(Collectors.toList()));

              if (userId != null) {
                userId = new ArrayList<>(new HashSet<>(userId));

                List<JsonObject> finalPolicies = policies;
                registrationService.getUserDetails(
                    userId,
                    res -> {
                      if (res.succeeded()) {
                        String uid;
                        String oid;
                        String itemType;
                        JsonObject object;
                        Map<String, JsonObject> map = jsonObjectToMap.apply(res.result());

                        for (JsonObject ar : finalPolicies) {
                          uid = ar.getString(USER_ID);
                          oid = ar.getString(OWNER_ID);
                          itemType = ar.getString("itemType");
                          ar.put("itemType", itemType.toLowerCase());
                          if (map.containsKey(uid)) {
                            ar.remove(USER_ID);
                            object = map.get(uid);
                            object.put("id", uid);
                            ar.put(USER_DETAILS, object);
                          }

                          if (map.containsKey(oid)) {
                            ar.remove(OWNER_ID);
                            object = map.get(oid);
                            object.put("id", oid);
                            ar.put(OWNER_DETAILS, object);
                          }
                          respPolicies.add(ar);
                        }
                        Response r =
                            new Response.ResponseBuilder()
                                .type(URN_SUCCESS)
                                .title(SUCC_TITLE_POLICY_READ)
                                .status(200)
                                .arrayResults(respPolicies)
                                .build();
                        handler.handle(Future.succeededFuture(r.toJson()));
                      } else if (res.failed()) {
                        LOGGER.error("Registration failure :" + res.cause());
                        handler.handle(Future.failedFuture(INTERNALERROR));
                      }
                    });
              } else {
                Response r =
                    new Response.ResponseBuilder()
                        .type(URN_SUCCESS)
                        .title(SUCC_TITLE_POLICY_READ)
                        .status(200)
                        .detail("no policies")
                        .build();
                handler.handle(Future.succeededFuture(r.toJson()));
              }
            })
        .onFailure(
            obj -> {
              LOGGER.error(obj.getMessage());
              handler.handle(Future.failedFuture(INTERNALERROR));
            });

    return this;
  }

  Future<JsonObject> verifyConsumerPolicy(
      UUID userId, String itemId, String itemType, Map<String, ResourceObj> resDetails) {
    Promise<JsonObject> p = Promise.promise();

    /*check itemType,
    if resGrp check only resGrp table
    else get resGrp from item id and check both res and resGrp tables as there may be a policy
    for the resGrp the res belongs to
     */

    Future<JsonObject> getResGrpConstraints;

    if (itemType.equals(itemTypes.RESOURCE_GROUP.toString())) {
      Collector<Row, ?, List<JsonObject>> constraintCollector =
          Collectors.mapping(row -> row.toJson(), Collectors.toList());
      getResGrpConstraints =
          pool.withConnection(
              conn ->
                  conn.preparedQuery(GET_CONSUMER_CONSTRAINTS)
                      .collecting(constraintCollector)
                      .execute(
                          Tuple.of(
                              userId,
                              resDetails.get(itemId).getId(),
                              itemTypes.RESOURCE_GROUP,
                              status.ACTIVE))
                      .map(res -> res.size() > 0 ? res.value().get(0) : null)
                      .onFailure(
                          failureHandler -> {
                            LOGGER.error(
                                "getResGrpConstraints db fail :: "
                                    + failureHandler.getLocalizedMessage());
                            p.fail(INTERNALERROR);
                          }));
    } else {
      getResGrpConstraints =
          pool.withConnection(
              conn ->
                  conn.preparedQuery(GET_CONSUMER_CONSTRAINTS)
                      .execute(
                          Tuple.of(
                              userId,
                              resDetails.get(itemId).getResGrpId(),
                              itemTypes.RESOURCE_GROUP,
                              status.ACTIVE))
                      .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
    }
    Future<JsonObject> getResItemConstraints;
    if (itemType.equals(itemTypes.RESOURCE.toString())) {
      getResItemConstraints =
          pool.withConnection(
              conn ->
                  conn.preparedQuery(GET_CONSUMER_CONSTRAINTS)
                      .execute(
                          Tuple.of(
                              userId,
                              resDetails.get(itemId).getId(),
                              itemTypes.RESOURCE,
                              status.ACTIVE))
                      .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
    } else {
      getResItemConstraints = Future.succeededFuture(new JsonObject());
    }

    Future<JsonObject> getConstraints =
        CompositeFuture.all(getResGrpConstraints, getResItemConstraints)
            .compose(
                ar -> {
                  if (itemType.equals(itemTypes.RESOURCE_GROUP.toString())) {
                    return getResGrpConstraints;
                  } else {
                    if (getResItemConstraints.result() == null) return getResGrpConstraints;
                    else {
                      return getResItemConstraints;
                    }
                  }
                });

    Future<String> getUrl =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(GET_URL)
                        .execute(Tuple.of(resDetails.get(itemId).getResServerID()))
                        .map(
                            rows ->
                                rows.rowCount() > 0 ? rows.iterator().next().getString(URL) : null))
            .compose(
                ar -> {
                  if (ar == null) {
                    Response r =
                        new ResponseBuilder()
                            .status(403)
                            .type(URN_INVALID_INPUT)
                            .title(INVALID_POLICY)
                            .detail(NO_RES_SERVER)
                            .build();
                    return Future.failedFuture(new ComposeException(r));
                  } else {
                    return Future.succeededFuture(ar);
                  }
                });

    CompositeFuture.all(getConstraints, getUrl)
        .onSuccess(
            success -> {
              if (getConstraints.result() != null && !getUrl.result().isEmpty()) {
                JsonObject details = new JsonObject();
                details.mergeIn(getConstraints.result());
                details.put(STATUS, SUCCESS);
                details.put(CAT_ID, itemId);
                details.put(URL, getUrl.result());
                p.complete(details);
              } else {
                Response r =
                    new ResponseBuilder()
                        .status(403)
                        .type(URN_INVALID_INPUT)
                        .title(INVALID_POLICY)
                        .detail(POLICY_NOT_FOUND)
                        .build();
                p.fail(new ComposeException(r));
              }
            })
        .onFailure(
            failureHandler -> {
              // check if compose Exception, p.fail(composeExp)

              LOGGER.error("failed verifyConsumerPolicy: " + failureHandler.getLocalizedMessage());
              if (failureHandler instanceof ComposeException)
                p.fail(failureHandler);
              else p.fail(INTERNALERROR);
            });
    return p.future();
  }

  // change email hash parameter instead of item id for provider flow
  Future<JsonObject> verifyProviderPolicy(
      UUID userId, String itemId, String email_hash, String itemType, boolean isCatalogue) {
    Promise<JsonObject> p = Promise.promise();

    Future<UUID> getResOwner =
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_RES_OWNER)
                    .execute(Tuple.of(email_hash))
                    .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null));

    Future<JsonObject> getResSerOwner;
    if (isCatalogue) {
      getResSerOwner =
          getResOwner.compose(
              ar -> {
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(NO_USER)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SERVER_OWNER)
                            .execute(Tuple.of(catServerOptions.getString("catURL")))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    } else {
      getResSerOwner =
          getResOwner.compose(
              ar -> {
                if (!getResOwner.result().equals(userId)) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(NOT_RES_OWNER)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SER_OWNER + itemType + GET_RES_SER_OWNER_JOIN)
                            .execute(Tuple.of(itemId))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    }

    Future<UUID> checkAdminPolicy =
        getResSerOwner.compose(
            success ->
            {
             return   pool.withConnection(
                        conn -> {
                          return conn.preparedQuery(CHECK_ADMIN_POLICY)
                              .execute(
                                  Tuple.of(
                                      userId,
                                      success.getString(OWNER_ID),
                                      success.getString(ID),
                                      itemTypes.RESOURCE_SERVER.toString(),
                                      status.ACTIVE.toString()));
                        })
                    .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null)
                    .compose(
                        obj -> {
                          if (obj == null) {
                            Response r =
                                new ResponseBuilder()
                                    .status(403)
                                    .type(URN_INVALID_INPUT)
                                    .title(INVALID_POLICY)
                                    .detail(NO_ADMIN_POLICY)
                                    .build();
                            return Future.failedFuture(new ComposeException(r));
                          }
                          return Future.succeededFuture(obj);
                        });});

    checkAdminPolicy
        .onSuccess(
            success -> {
              if (!success.toString().isEmpty()) {
                JsonObject details = new JsonObject();
                details.put(STATUS, SUCCESS);
                details.put(CAT_ID, itemId);
                details.put(URL, getResSerOwner.result().getString("url"));
                p.complete(details);
              }
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error("failed verifyProviderPolicy: " + failureHandler.getLocalizedMessage());
              if (failureHandler instanceof ComposeException) {
                p.fail(failureHandler);
              } else p.fail(INTERNALERROR);
            });

    return p.future();
  }

  Future<JsonObject> verifyDelegatePolicy(
      UUID userId,
      String itemId,
      String email_hash,
      String itemType,
      Map<String, ResourceObj> resDetails,
      boolean isCatalogue) {
    Promise<JsonObject> p = Promise.promise();

    Future<UUID> getOwner;
    if (isCatalogue)
      getOwner =
          pool.withConnection(
              conn ->
                  conn.preparedQuery(GET_RES_OWNER)
                      .execute(Tuple.of(email_hash))
                      .map(
                          rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null));
    else getOwner = Future.succeededFuture(resDetails.get(itemId).getOwnerId());

    Future<JsonObject> getResSerOwner;
    if (isCatalogue) {
      getResSerOwner =
          getOwner.compose(
              ar -> {
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(NO_USER)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SERVER_OWNER)
                            .execute(Tuple.of(catServerOptions.getString("catURL")))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    } else {
      getResSerOwner =
          getOwner.compose(
              ar -> {
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(NO_RES_SERVER)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SERVER_OWNER_ID)
                            .execute(Tuple.of(resDetails.get(itemId).getResServerID()))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    }

    Future<UUID> checkDelegation =
        getResSerOwner.compose(
            ar -> {
              if (ar == null) {
                Response r =
                    new ResponseBuilder()
                        .status(403)
                        .type(URN_INVALID_INPUT)
                        .title(INVALID_POLICY)
                        .detail(UNAUTHORIZED_DELEGATE)
                        .build();
                return Future.failedFuture(new ComposeException(r));
              }
              return pool.withConnection(
                  conn ->
                      conn.preparedQuery(CHECK_DELEGATOINS_VERIFY)
                          .execute(
                              Tuple.of(
                                  userId,
                                  getOwner.result(),
                                  getResSerOwner.result().getString(ID),
                                  status.ACTIVE.toString()))
                          .map(
                              rows ->
                                  rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null));
            });

    Future<JsonObject> checkPolicy;
    Future<JsonObject> checkResGrpPolicy;
    Future<JsonObject> checkResPolicy;
    if (isCatalogue) {
      checkPolicy =
          checkDelegation.compose(
              obj -> {
                if (obj == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(UNAUTHORIZED_DELEGATE)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return Future.succeededFuture(new JsonObject());
              });
    } else {

      checkResGrpPolicy =
          checkDelegation.compose(
              ar -> {
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(UNAUTHORIZED_DELEGATE)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(CHECK_POLICY)
                            .execute(
                                Tuple.of(
                                    userId,
                                    getOwner.result(),
                                    resDetails.get(itemId).getResGrpId(),
                                    status.ACTIVE.toString()))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });

      checkResPolicy =
          checkDelegation.compose(
              ar -> {
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(UNAUTHORIZED_DELEGATE)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(CHECK_POLICY)
                            .execute(
                                Tuple.of(
                                    userId,
                                    getOwner.result(),
                                    resDetails.get(itemId).getId(),
                                    status.ACTIVE.toString()))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });

      checkPolicy =
          CompositeFuture.all(checkResGrpPolicy, checkResPolicy)
              .compose(
                  ar -> {
                    if (itemType.equals(itemTypes.RESOURCE_GROUP.toString()))
                      return checkResGrpPolicy;
                    else {
                      if (checkResPolicy.result() == null) return checkResGrpPolicy;
                      return checkResPolicy;
                    }
                  });
    }

    Future<UUID> checkAdminPolicy =
        checkPolicy.compose(
            success ->
                pool.withConnection(
                        conn -> {
                          if (checkPolicy.result() == null) {
                            Response r =
                                new ResponseBuilder()
                                    .status(403)
                                    .type(URN_INVALID_INPUT)
                                    .title(INVALID_POLICY)
                                    .detail(UNAUTHORIZED_DELEGATE)
                                    .build();
                            return Future.failedFuture(new ComposeException(r));
                          }
                          return conn.preparedQuery(CHECK_ADMIN_POLICY)
                              .execute(
                                  Tuple.of(
                                      userId,
                                      getResSerOwner.result().getString(OWNER_ID),
                                      getResSerOwner.result().getString(ID),
                                      itemTypes.RESOURCE_SERVER.toString(),
                                      status.ACTIVE.toString()));
                        })
                    .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null)
                    .compose(
                        obj -> {
                          if (obj == null) {
                            Response r =
                                new ResponseBuilder()
                                    .status(403)
                                    .type(URN_INVALID_INPUT)
                                    .title(INVALID_POLICY)
                                    .detail(NO_ADMIN_POLICY)
                                    .build();
                            return Future.failedFuture(new ComposeException(r));
                          }
                          return Future.succeededFuture(obj);
                        }));

    checkAdminPolicy
        .onSuccess(
            success -> {
              if (!success.toString().isEmpty()) {
                JsonObject details = new JsonObject();
                details.put(STATUS, SUCCESS);
                details.put(CAT_ID, itemId);
                details.put(URL, getResSerOwner.result().getString(URL));
                p.complete(details);
              }
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error("failed verifyDelegatePolicy ");
              if (failureHandler instanceof ComposeException) {
                ComposeException exp = (ComposeException) failureHandler;
                p.fail(failureHandler);
              } else p.fail(INTERNALERROR);
            });

    return p.future();
  }

  @Override
  public PolicyService verifyPolicy(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    UUID userId = UUID.fromString(request.getString(USERID));
    String itemId = request.getString(ITEMID);
    String itemType = request.getString(ITEMTYPE).toUpperCase();
    String role = request.getString(ROLE).toUpperCase();

    boolean isCatalogue = false;
    // verify policy does not expect the resServer itemType
    if (itemType.equals(itemTypes.RESOURCE_SERVER.toString())) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(INVALID_POLICY)
              .detail(INCORRECT_ITEM_TYPE)
              .build();
      handler.handle(Future.failedFuture(new ComposeException(r)));
      return this;
    }

    if (role.equals(roles.ADMIN.toString())) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(INVALID_POLICY)
              .detail(INVALID_ROLE)
              .build();
      handler.handle(Future.failedFuture(new ComposeException(r)));
      return this;
    }

    String[] itemSplit = itemId.split("/");

    String emailHash = itemSplit[0] + "/" + itemSplit[1];
    Future<String> getRoles =
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_FROM_ROLES_TABLE)
                    .execute(Tuple.of(userId, roles.valueOf(role), status.APPROVED))
                    .compose(
                        ar -> {
                          if (ar.rowCount() > 0) {
                            return Future.succeededFuture(ar.iterator().next().getString(ROLE));
                          } else {
                            Response r =
                                new ResponseBuilder()
                                    .status(403)
                                    .type(URN_INVALID_INPUT)
                                    .title(INVALID_POLICY)
                                    .detail(INVALID_ROLE)
                                    .build();
                            return Future.failedFuture(new ComposeException(r));
                          }
                        }));

    if (itemSplit.length == 5
        && itemSplit[2].equals(catServerOptions.getString("catURL"))
        && (itemSplit[3] + "/" + itemSplit[4]).equals(catServerOptions.getString("catItem"))) {

      isCatalogue = true;
    }

    Future<Map<String, ResourceObj>> reqItemDetail;
    if (!isCatalogue) {

      if (itemType.equals(itemTypes.RESOURCE_GROUP.toString()) && itemId.split("/").length != 4) {
        Response r =
            new ResponseBuilder()
                .status(403)
                .type(URN_INVALID_INPUT)
                .title(INVALID_POLICY)
                .detail(INCORRECT_ITEM_TYPE)
                .build();
        handler.handle(Future.failedFuture(new ComposeException(r)));
        return this;
      }

      if (itemType.equals(itemTypes.RESOURCE.toString()) && itemId.split("/").length <= 4) {
        Response r =
            new ResponseBuilder()
                .status(403)
                .type(URN_INVALID_INPUT)
                .title(INVALID_POLICY)
                .detail(INCORRECT_ITEM_TYPE)
                .build();
        handler.handle(Future.failedFuture(new ComposeException(r)));
        return this;
      }

      // create map of item, use catalogue client - checkReqItems to check and fetch item
      Map<String, List<String>> catItem = new HashMap<>();
      if (itemType.equals(itemTypes.RESOURCE.toString())) {

        ArrayList<String> list = new ArrayList<>();
        list.add(itemId);
        catItem.put(RES, list);
      }
      if (itemType.equals(itemTypes.RESOURCE_GROUP.toString())) {
        ArrayList<String> list = new ArrayList<>();
        list.add(itemId);
        catItem.put(RES_GRP, list);
      }
      reqItemDetail = catalogueClient.checkReqItems(catItem);
      // trim itemid for resitem type

    } else {
      reqItemDetail = Future.succeededFuture(new HashMap<>());
    }

    boolean finalIsCatalogue = isCatalogue;
    String finalItem = itemId;
    Future<JsonObject> verifyRolePolicy =
        CompositeFuture.all(getRoles, reqItemDetail)
            .compose(
                success -> {
                  Future<JsonObject> response;
                  switch (getRoles.result()) {
                    case CONSUMER_ROLE:
                      {
                        response =
                            verifyConsumerPolicy(
                                userId, finalItem, itemType, reqItemDetail.result());
                        break;
                      }
                    case PROVIDER_ROLE:
                      {
                        response =
                            verifyProviderPolicy(
                                userId, finalItem, emailHash, itemType, finalIsCatalogue);
                        break;
                      }
                    case DELEGATE_ROLE:
                      {
                        response =
                            verifyDelegatePolicy(
                                userId,
                                finalItem,
                                emailHash,
                                itemType,
                                reqItemDetail.result(),
                                finalIsCatalogue);
                        break;
                      }
                    default:
                      {
                        response = Future.failedFuture(INTERNALERROR);
                      }
                  }
                  return response;
                });

    verifyRolePolicy.onSuccess(
        s -> {
          handler.handle(Future.succeededFuture(s));
        });

    verifyRolePolicy.onFailure(
        e -> {
          LOGGER.error("verifyRolePolicy failed" + e.getMessage());
          if (e instanceof ComposeException) {
            handler.handle(Future.failedFuture(e));
          } else handler.handle(Future.failedFuture(INTERNALERROR));
        });

    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService setDefaultProviderPolicies(
      List<String> userIds, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    handler.handle(Future.succeededFuture(new JsonObject()));
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService createPolicyNotification(
      List<CreatePolicyNotification> request, User user, Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    List<Roles> roles = user.getRoles();
    JsonObject userJson = user.toJson();
    userJson.remove("keycloakId");
    userJson.remove("roles");

    if (!roles.contains(Roles.CONSUMER)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(INVALID_ROLE)
              .detail(INVALID_ROLE)
              .status(403)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<String> resServerIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject
                        .getItemType()
                        .toUpperCase()
                        .equals(itemTypes.RESOURCE_SERVER.toString()))
            .map(CreatePolicyNotification::getItemId)
            .collect(Collectors.toList());

    List<String> resGrpIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject
                        .getItemType()
                        .toUpperCase()
                        .equals(itemTypes.RESOURCE_GROUP.toString()))
            .map(CreatePolicyNotification::getItemId)
            .collect(Collectors.toList());

    if (!resGrpIds.stream().allMatch(itemTypeCheck -> itemTypeCheck.split("/").length == 4)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(INCORRECT_ITEM_TYPE)
              .detail(INCORRECT_ITEM_TYPE)
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<String> resIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject.getItemType().toUpperCase().equals(itemTypes.RESOURCE.toString()))
            .map(CreatePolicyNotification::getItemId)
            .collect(Collectors.toList());

    if (!resIds.stream().allMatch(itemTypeCheck -> itemTypeCheck.split("/").length > 4)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(INCORRECT_ITEM_TYPE)
              .detail(INCORRECT_ITEM_TYPE)
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Map<String, List<String>> catItem = new HashMap<>();

    if (resServerIds.size() > 0) {
      catItem.put(RES_SERVER, resServerIds);
    } else {
      if (resGrpIds.size() > 0) {
        catItem.put(RES_GRP, resGrpIds);
      }
      if (resIds.size() > 0) {
        catItem.put(RES, resIds);
      }
    }

    Future<Map<String, ResourceObj>> reqCatItem = catalogueClient.checkReqItems(catItem);

    reqCatItem.onComplete(
        dbHandler -> {
          if (dbHandler.failed()) {
            LOGGER.error("reqCatItem failed " + dbHandler.cause());
            Response resp =
                new ResponseBuilder()
                    .status(400)
                    .type(URN_INVALID_INPUT)
                    .title(ITEMNOTFOUND)
                    .detail(ITEMNOTFOUND)
                    .build();
            handler.handle(Future.succeededFuture(resp.toJson()));
            return;
          }

          if (dbHandler.succeeded()) {
            Future<List<Tuple>> tuples = mapTupleCreate(request, reqCatItem.result(), user);
            Future<List<Tuple>> checkDuplicate = checkDuplication(tuples.result());

            CompositeFuture.all(tuples, checkDuplicate)
                .onComplete(
                    resHandler -> {
                      if (resHandler.failed()) {
                        String msg = URN_INVALID_INPUT.toString();
                        int status = 400;
                        if (checkDuplicate.failed()) {
                          msg = URN_ALREADY_EXISTS.toString();
                          status = 409;
                        }

                        LOGGER.error(LOG_DB_ERROR + resHandler.cause().getLocalizedMessage());
                        Response resp =
                            new ResponseBuilder()
                                .status(status)
                                .type(msg)
                                .title(resHandler.cause().getLocalizedMessage())
                                .detail(resHandler.cause().getLocalizedMessage())
                                .build();
                        handler.handle(Future.succeededFuture(resp.toJson()));
                        return;
                      }

                      if (resHandler.succeeded()) {

                        pool.withTransaction(
                            conn ->
                                conn.preparedQuery(CREATE_NOTIFI_POLICY_REQUEST)
                                    .executeBatch(tuples.result())
                                    .onComplete(
                                        insertHandler -> {
                                          if (insertHandler.failed()) {
                                            LOGGER.error(
                                                LOG_DB_ERROR
                                                    + insertHandler.cause().getLocalizedMessage());
                                            Response resp =
                                                new ResponseBuilder()
                                                    .status(400)
                                                    .type(URN_INVALID_INPUT)
                                                    .title(INTERNALERROR)
                                                    .detail(INTERNALERROR)
                                                    .build();
                                            handler.handle(Future.succeededFuture(resp.toJson()));
                                            return;
                                          }

                                          if (insertHandler.succeeded()) {
                                            RowSet<Row> rows = insertHandler.result();
                                            JsonArray resp = new JsonArray();
                                            while (rows != null) {
                                              rows.iterator()
                                                  .forEachRemaining(
                                                      row -> {
                                                        resp.add(row.toJson());
                                                      });
                                              rows = rows.next();
                                            }

                                            List<String> ids = new ArrayList<>();
                                            ids.add(user.getUserId());

                                            List<String> ownerIds =
                                                dbHandler.result().values().stream()
                                                    .map(resObj -> resObj.getOwnerId().toString())
                                                    .collect(Collectors.toList());
                                            ids.addAll(ownerIds);

                                            registrationService.getUserDetails(
                                                ids,
                                                userHandler -> {
                                                  if (userHandler.failed()) {
                                                    LOGGER.error(
                                                        "Fail: Registration failure; "
                                                            + userHandler.cause());
                                                    handler.handle(
                                                        Future.failedFuture(INTERNALERROR));
                                                  }

                                                  if (userHandler.succeeded()) {
                                                    Map<String, JsonObject> userInfo =
                                                        jsonObjectToMap.apply(userHandler.result());

                                                    JsonObject userJson1 =
                                                        userInfo.get(user.getUserId());
                                                    userJson1.put(ID, user.getUserId());

                                                    JsonArray results = new JsonArray();
                                                    for (int i = 0; i < request.size(); i++) {
                                                      JsonObject requestJson =
                                                          request.get(i).toJson();
                                                      String ownerId = ownerIds.get(i);
                                                      JsonObject ownerInfo = userInfo.get(ownerId);
                                                      ownerInfo.put(ID, ownerId);
                                                      JsonObject eachJson =
                                                          resp.getJsonObject(i)
                                                              .copy()
                                                              .mergeIn(requestJson)
                                                              .put(USER_DETAILS, userJson1)
                                                              .put(OWNER_DETAILS, ownerInfo);
                                                      results.add(eachJson);
                                                    }

                                                    LOGGER.info(
                                                        "Success: {}; Id: {}",
                                                        SUCC_NOTIF_REQ,
                                                        resp);
                                                    Response res =
                                                        new Response.ResponseBuilder()
                                                            .type(URN_SUCCESS)
                                                            .title(SUCC_TITLE_POLICY_READ)
                                                            .status(200)
                                                            .arrayResults(results)
                                                            .build();
                                                    handler.handle(
                                                        Future.succeededFuture(res.toJson()));
                                                  }
                                                });
                                          }
                                        }));
                      }
                    });
          }
        });
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService listPolicyNotification(
      User user, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {

    boolean isDelegate = !data.isEmpty();
    List<Roles> roles = user.getRoles();

    if (!(isDelegate || roles.contains(Roles.PROVIDER) || roles.contains(Roles.CONSUMER))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES)
              .status(401)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    String query;
    Tuple queryTuple;

    if (isDelegate) {
      UUID providerId = UUID.fromString(data.getString("providerId"));
      query = SELECT_PROVIDER_NOTIF_REQ;
      queryTuple = Tuple.of(providerId);
    } else if (roles.contains(Roles.PROVIDER)) {
      query = SELECT_PROVIDER_NOTIF_REQ;
      queryTuple = Tuple.of(user.getUserId());
    } else {
      query = SELECT_CONSUM_NOTIF_REQ;
      queryTuple = Tuple.of(user.getUserId());
    }

    Collector<Row, ?, List<JsonObject>> collect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> notifData =
        pool.withTransaction(
            conn ->
                conn.query(SET_INTERVALSTYLE)
                    .execute()
                    .flatMap(
                        result ->
                            conn.preparedQuery(query)
                                .collecting(collect)
                                .execute(queryTuple)
                                .map(res -> res.value())));

    Future<JsonArray> itemNames =
        notifData.compose(
            result -> {
              Promise<JsonArray> promise = Promise.promise();

              Collector<Row, ?, Map<UUID, String>> collectItemName =
                  Collectors.toMap(row -> row.getUUID(ID), row -> row.getString(URL));

              List<UUID> itemIds =
                  result.stream()
                      .map(each -> UUID.fromString(each.getString(ITEMID)))
                      .collect(Collectors.toList());

              Tuple tuple = Tuple.of(itemIds.toArray(UUID[]::new));
              Future<Map<UUID, String>> getNames =
                  pool.withTransaction(
                      conn ->
                          conn.preparedQuery(SEL_NOTIF_ITEM_ID)
                              .collecting(collectItemName)
                              .execute(tuple)
                              .map(res -> res.value()));

              getNames
                  .onFailure(
                      failureHandler -> {
                        promise.fail(failureHandler.getLocalizedMessage());
                      })
                  .onSuccess(
                      nameMapper -> {
                        JsonArray resArr = new JsonArray();
                        result.forEach(
                            each -> {
                              UUID itemId = UUID.fromString(each.getString(ITEMID));
                              each.put(ITEMID, nameMapper.get(itemId));
                              resArr.add(each);
                            });
                        promise.complete(resArr);
                      });

              return promise.future();
            });

    Future<JsonObject> userInfo =
        itemNames.compose(
            result -> {
              Set<String> ids = new HashSet<String>();
              result.forEach(
                  obj -> {
                    JsonObject each = (JsonObject) obj;
                    ids.add(each.getString(OWNER_ID));
                    ids.add(each.getString(USER_ID));
                  });

              Promise<JsonObject> userDetails = Promise.promise();
              registrationService.getUserDetails(new ArrayList<String>(ids), userDetails);
              return userDetails.future();
            });

    userInfo
        .onSuccess(
            result -> {
              JsonArray notifRequest = itemNames.result();
              JsonArray response = new JsonArray();
              Map<String, JsonObject> details = jsonObjectToMap.apply(result);

              notifRequest.forEach(
                  obj -> {
                    JsonObject each = (JsonObject) obj;

                    String userId = (String) each.remove(USER_ID);
                    String ownerId = (String) each.remove(OWNER_ID);

                    JsonObject eachDetails =
                        each.copy()
                            .put(USER_DETAILS, details.get(userId).put(ID, userId))
                            .put(OWNER_DETAILS, details.get(ownerId).put(ID, ownerId));

                    response.add(eachDetails);
                  });

              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_LIST_NOTIF_REQ)
                      .arrayResults(response)
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            e -> {
              LOGGER.error(ERR_LIST_NOTIF + "; {}", e.getMessage());
              handler.handle(Future.failedFuture(INTERNALERROR));
            });

    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService updatelistPolicyNotification(
      List<UpdatePolicyNotification> request,
      User user,
      JsonObject data,
      Handler<AsyncResult<JsonObject>> handler) {

    boolean isDelegate = !data.isEmpty();
    List<Roles> roles = user.getRoles();

    if (!((isDelegate && roles.contains(Roles.DELEGATE)) || roles.contains(Roles.PROVIDER))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES)
              .status(401)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<UUID> requestIds =
        request.stream()
            .map(each -> UUID.fromString(each.getRequestId()))
            .collect(Collectors.toList());

    Set<UUID> uniqueRequestIds = new HashSet<UUID>(requestIds);
    if (requestIds.size() != uniqueRequestIds.size()) {
      LOGGER.error("Fail: {}", DUPLICATE);
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(SUCC_NOTIF_REQ)
              .detail(DUPLICATE)
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Collector<Row, ?, List<JsonObject>> notifRequestCollect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Map<UUID, JsonObject> requestMap =
        request.stream()
            .collect(
                Collectors.toMap(
                    key -> UUID.fromString(key.getRequestId()), value -> value.toJson()));

    Tuple tup = Tuple.of(requestIds.toArray(UUID[]::new));
    Future<List<JsonObject>> policyRequestData =
        pool.withTransaction(
            conn ->
                conn.preparedQuery(SET_INTERVALSTYLE)
                    .execute()
                    .flatMap(
                        result ->
                            conn.preparedQuery(SEL_NOTIF_REQ_ID)
                                .collecting(notifRequestCollect)
                                .execute(tup)
                                .map(res -> res.value())));

    policyRequestData.onComplete(
        dbHandler -> {
          if (dbHandler.failed()) {
            LOGGER.error(
                LOG_DB_ERROR + " {}",
                dbHandler.cause() == null ? dbHandler.result() : dbHandler.cause().getMessage());
            handler.handle(Future.failedFuture(INTERNALERROR));
            return;
          }

          if (dbHandler.succeeded()) {
            if (dbHandler.result().size() != request.size()) {
              LOGGER.debug("Info: {}", REQ_ID_ALREADY_NOT_EXISTS);

              Response resp =
                  new ResponseBuilder()
                      .status(404)
                      .type(URN_INVALID_INPUT)
                      .title(SUCC_NOTIF_REQ)
                      .detail(REQ_ID_ALREADY_NOT_EXISTS)
                      .build();
              handler.handle(Future.succeededFuture(resp.toJson()));
              return;
            }

            List<JsonObject> notifReqlist = dbHandler.result();
            JsonArray createPolicyArr = new JsonArray();

            notifReqlist.forEach(
                each -> {
                  UUID requestId = UUID.fromString(each.getString(ID));
                  JsonObject requestJson = requestMap.get(requestId);
                  if (requestJson != null) {
                    JsonObject temp = each.copy().mergeIn(requestJson, Boolean.TRUE);
                    createPolicyArr.add(temp);
                  }
                });

            List<String> ownerIds =
                createPolicyArr.stream()
                    .map(JsonObject.class::cast)
                    .map(each -> each.getString(OWNERID))
                    .collect(Collectors.toList());

            List<UUID> itemIds =
                createPolicyArr.stream()
                    .map(JsonObject.class::cast)
                    .map(each -> UUID.fromString(each.getString(ITEMID)))
                    .collect(Collectors.toList());

            Collector<Row, ?, Map<UUID, String>> collectItemName =
                Collectors.toMap(row -> row.getUUID(ID), row -> row.getString(URL));

            Future<Map<UUID, String>> getItemIdName =
                pool.withTransaction(
                    conn ->
                        conn.preparedQuery(SEL_NOTIF_ITEM_ID)
                            .collecting(collectItemName)
                            .execute(Tuple.of(itemIds.toArray(UUID[]::new)))
                            .map(res -> res.value()));

            getItemIdName.onComplete(
                getHandler -> {
                  if (getHandler.failed()) {
                    LOGGER.error(LOG_DB_ERROR + " {}", getHandler.cause().getMessage());
                    handler.handle(Future.failedFuture(INTERNALERROR));
                    return;
                  }

                  if (getHandler.succeeded()) {

                    Map<UUID, String> idMap = getHandler.result();

                    LocalDateTime start = LocalDateTime.now();
                    List<Tuple> selectPolicy = new ArrayList<>();
                    JsonArray resArr = new JsonArray();

                    JsonArray approvedReq =
                        createPolicyArr.stream()
                            .map(JsonObject.class::cast)
                            .filter(
                                each -> each.getString(STATUS).equals(RoleStatus.APPROVED.name()))
                            .map(
                                each -> {
                                  String expiry = each.getString("expiryDuration");
                                  String itemId = each.getString(ITEMID);

                                  org.joda.time.Interval interval =
                                      org.joda.time.Interval.parse(start + "/" + expiry);
                                  each.put(EXPIRYTIME, interval.getEnd().toString());
                                  each.put(ITEMID, idMap.get(UUID.fromString(itemId)));
                                  selectPolicy.add(
                                      Tuple.of(
                                          each.getString(USERID), itemId, each.getString(OWNERID)));

                                  return each;
                                })
                            .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add));

                    List<Tuple> updateRejectedReq =
                        createPolicyArr.stream()
                            .map(JsonObject.class::cast)
                            .filter(
                                each -> each.getString(STATUS).equals(RoleStatus.REJECTED.name()))
                            .map(
                                each -> {
                                  UUID requestId = UUID.fromString(each.getString("requestId"));
                                  String status = each.getString(STATUS);

                                  String itemId = each.getString(ITEMID);
                                  each.put(ITEMID, idMap.get(UUID.fromString(itemId)));
                                  each.put(STATUS, status.toLowerCase());
                                  resArr.add(each);
                                  return Tuple.of(requestId, status);
                                })
                            .collect(Collectors.toList());

                    Future<Object> updateDb =
                        Future.future(
                            futureHandler -> {
                              if (!approvedReq.isEmpty()) {
                                List<UpdatePolicyNotification> updateReq =
                                    UpdatePolicyNotification.jsonArrayToList(approvedReq);
                                Future<List<Tuple>> updateTuple = mapTupleUpdate(updateReq);
                                updateTuple
                                    .compose(
                                        mapper -> {
                                          return pool.withTransaction(
                                              conn ->
                                                  conn.preparedQuery(UPDATE_NOTIF_REQ_APPROVED)
                                                      .executeBatch(mapper)
                                                      .map(res -> res.value()));
                                        })
                                    .onFailure(
                                        updateFailHandler -> {
                                          futureHandler.fail(updateFailHandler.getCause());
                                          return;
                                        })
                                    .onSuccess(
                                        updateSuccHandler -> {
                                          if (updateSuccHandler.rowCount() == 0) {
                                            futureHandler.fail(REQ_ID_ALREADY_PROCESSED);
                                            return;
                                          }
                                          List<CreatePolicyRequest> createPolicyArray =
                                              CreatePolicyRequest.jsonArrayToList(approvedReq);

                                          createPolicy(
                                              createPolicyArray,
                                              user,
                                                  new JsonObject(),
                                              createHandler -> {
                                                if (createHandler.failed()) {
                                                  handler.handle(
                                                      Future.succeededFuture(
                                                          createHandler.result()));
                                                  return;
                                                }

                                                if (createHandler.succeeded()) {
                                                  JsonObject result = createHandler.result();
                                                  if (URN_SUCCESS
                                                      .toString()
                                                      .equalsIgnoreCase(result.getString(TYPE))) {

                                                    Collector<Row, ?, List<Tuple>> policyCollector =
                                                        Collectors.mapping(
                                                            row ->
                                                                Tuple.of(
                                                                    row.getUUID("requestId"),
                                                                    row.getUUID("policyId")),
                                                            Collectors.toList());

                                                    Future<Object> insertQuery =
                                                        pool.withTransaction(
                                                            conn ->
                                                                conn.preparedQuery(
                                                                        SEL_NOTIF_POLICY_ID)
                                                                    .collecting(policyCollector)
                                                                    .executeBatch(selectPolicy)
                                                                    .flatMap(
                                                                        insert ->
                                                                            conn.preparedQuery(
                                                                                    INSERT_NOTIF_APPROVED_ID)
                                                                                .executeBatch(
                                                                                    insert.value())
                                                                                .map(
                                                                                    res ->
                                                                                        res
                                                                                            .value())));

                                                    futureHandler.complete(insertQuery);
                                                    return;
                                                  } else {
                                                    handler.handle(
                                                        Future.succeededFuture(
                                                            createHandler.result()));
                                                  }
                                                }
                                              });
                                        });
                              } else if (!updateRejectedReq.isEmpty()) {
                                Future<Integer> updateRejected =
                                    pool.withTransaction(
                                        conn ->
                                            conn.preparedQuery(UPDATE_NOTIF_REQ_REJECTED)
                                                .executeBatch(updateRejectedReq)
                                                .map(res -> res.value().rowCount()));

                                updateRejected.onComplete(
                                    comHandler -> {
                                      if (comHandler.succeeded() && comHandler.result() == 0) {
                                        futureHandler.fail(REQ_ID_ALREADY_PROCESSED);
                                      } else {
                                        futureHandler.complete(updateRejected.result());
                                      }
                                    });
                              }
                            });

                    updateDb.onComplete(
                        updateHandler -> {
                          if (updateHandler.failed()) {
                            String msg = updateHandler.cause().getMessage();
                            LOGGER.error("Fail: {}", msg);
                            if (msg.contains(INTERNALERROR)) {
                              handler.handle(
                                  Future.failedFuture(updateHandler.cause().getMessage()));
                            } else {
                              Response res =
                                  new Response.ResponseBuilder()
                                      .type(URN_INVALID_INPUT)
                                      .title(SUCC_NOTIF_REQ)
                                      .detail(msg)
                                      .status(400)
                                      .build();
                              handler.handle(Future.succeededFuture(res.toJson()));
                            }
                            return;

                          } else if (updateHandler.succeeded()) {

                            List<String> ids = new ArrayList<>();
                            ids.add(user.getUserId());
                            ids.addAll(ownerIds);

                            registrationService.getUserDetails(
                                ids,
                                userHandler -> {
                                  if (userHandler.failed()) {
                                    LOGGER.error(
                                        "Fail: Registration failure; " + userHandler.cause());
                                    handler.handle(Future.failedFuture(INTERNALERROR));
                                    return;
                                  }

                                  if (userHandler.succeeded()) {
                                    Map<String, JsonObject> userInfo =
                                        jsonObjectToMap.apply(userHandler.result());

                                    JsonObject userJson1 = userInfo.get(user.getUserId());

                                    JsonArray results = new JsonArray();
                                    resArr.addAll(approvedReq);
                                    for (int i = 0; i < resArr.size(); i++) {
                                      JsonObject requestJson = resArr.getJsonObject(i);

                                      requestJson.remove(EXPIRYTIME);
                                      requestJson.remove(USERID);
                                      requestJson.remove(OWNERID);
                                      requestJson.remove(ID);
                                      requestJson.put(
                                          STATUS, requestJson.getString(STATUS).toLowerCase());
                                      requestJson.put(
                                          ITEMTYPE, requestJson.getString(ITEMTYPE).toLowerCase());

                                      JsonObject eachJson =
                                          requestJson
                                              .put(USER_DETAILS, userJson1)
                                              .put(OWNER_DETAILS, userInfo.get(ownerIds.get(i)));

                                      results.add(eachJson);
                                    }

                                    LOGGER.info(
                                        "Success: {}; {}", SUCC_NOTIF_REQ, SUCC_UPDATE_NOTIF_REQ);
                                    Response res =
                                        new Response.ResponseBuilder()
                                            .type(URN_SUCCESS)
                                            .title(SUCC_UPDATE_NOTIF_REQ)
                                            .status(200)
                                            .arrayResults(results)
                                            .build();
                                    handler.handle(Future.succeededFuture(res.toJson()));
                                    return;
                                  }
                                });
                          }
                        });
                  }
                });
          }
        });
    return this;
  }

  @Override
  public PolicyService listDelegation(
      User user, JsonObject authDelegateDetails, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    boolean isAuthDelegate = !authDelegateDetails.isEmpty();

    if (!(isAuthDelegate
        || user.getRoles().contains(Roles.PROVIDER)
        || user.getRoles().contains(Roles.DELEGATE))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES)
              .status(401)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    String query;
    Tuple queryTup;

    /* get all delegations EXCEPT auth server delegations */
    if (isAuthDelegate) {
      UUID providerUserId = UUID.fromString(authDelegateDetails.getString("providerId"));
      query = LIST_DELEGATE_AUTH_DELEGATE;
      queryTup = Tuple.of(providerUserId, authOptions.getString("authServerUrl"));
    } else {
      query = LIST_DELEGATE_AS_PROVIDER_DELEGATE;
      queryTup = Tuple.of(UUID.fromString(user.getUserId()));
    }

    Collector<Row, ?, List<JsonObject>> collect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> data =
        pool.withConnection(
            conn ->
                conn.preparedQuery(query)
                    .collecting(collect)
                    .execute(queryTup)
                    .map(res -> res.value()));

    Future<JsonObject> userInfo =
        data.compose(
            result -> {
              Set<String> ss = new HashSet<String>();
              result.forEach(
                  obj -> {
                    ss.add(obj.getString("owner_id"));
                    ss.add(obj.getString("user_id"));
                  });

              Promise<JsonObject> userDetails = Promise.promise();
              registrationService.getUserDetails(new ArrayList<String>(ss), userDetails);
              return userDetails.future();
            });

    userInfo
        .onSuccess(
            results -> {
              List<JsonObject> deleRes = data.result();
              Map<String, JsonObject> details = jsonObjectToMap.apply(results);

              deleRes.forEach(
                  obj -> {
                    JsonObject ownerDet = details.get(obj.getString("owner_id"));
                    ownerDet.put("id", obj.remove("owner_id"));

                    JsonObject userDet = details.get(obj.getString("user_id"));
                    userDet.put("id", obj.remove("user_id"));

                    obj.put("owner", ownerDet);
                    obj.put("user", userDet);
                  });
              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_LIST_DELEGS)
                      .arrayResults(new JsonArray(deleRes))
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            e -> {
              LOGGER.error(e.getMessage());
              handler.handle(Future.failedFuture("Internal error"));
            });
    return this;
  }

  @Override
  public PolicyService deleteDelegation(
      List<DeleteDelegationRequest> request,
      User user,
      JsonObject authDelegateDetails,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    boolean isAuthDelegate = !authDelegateDetails.isEmpty();

    if (!(isAuthDelegate || user.getRoles().contains(Roles.PROVIDER))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_DEL_DELEGATE_ROLES)
              .status(401)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Tuple queryTup;
    List<UUID> ids =
        request.stream().map(obj -> UUID.fromString(obj.getId())).collect(Collectors.toList());

    if (isAuthDelegate) {
      UUID providerUserId = UUID.fromString(authDelegateDetails.getString("providerId"));
      queryTup = Tuple.of(providerUserId).addArrayOfUUID(ids.toArray(UUID[]::new));
    } else {
      queryTup =
          Tuple.of(UUID.fromString(user.getUserId())).addArrayOfUUID(ids.toArray(UUID[]::new));
    }

    Collector<Row, ?, Map<UUID, String>> collect =
        Collectors.toMap(row -> row.getUUID("id"), row -> row.getString("url"));

    Future<Map<UUID, String>> idServerMap =
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_DELEGATIONS_BY_ID)
                    .collecting(collect)
                    .execute(queryTup)
                    .map(res -> res.value()));

    Future<Void> validate =
        idServerMap.compose(
            data -> {
              if (data.size() != ids.size()) {
                List<UUID> badIds =
                    ids.stream().filter(id -> !data.containsKey(id)).collect(Collectors.toList());

                Response r =
                    new Response.ResponseBuilder()
                        .type(URN_INVALID_INPUT)
                        .title(ERR_TITLE_INVALID_ID)
                        .detail(badIds.get(0).toString())
                        .status(400)
                        .build();
                handler.handle(Future.succeededFuture(r.toJson()));
                return Future.failedFuture(COMPOSE_FAILURE);
              }

              if (!isAuthDelegate) {
                return Future.succeededFuture();
              }

              List<UUID> authDelegs =
                  data.entrySet().stream()
                      .filter(obj -> obj.getValue().equals(authOptions.getString("authServerUrl")))
                      .map(obj -> obj.getKey())
                      .collect(Collectors.toList());

              if (!authDelegs.isEmpty()) {
                Response r =
                    new Response.ResponseBuilder()
                        .type(URN_INVALID_INPUT)
                        .title(ERR_TITLE_AUTH_DELE_DELETE)
                        .detail(authDelegs.get(0).toString())
                        .status(403)
                        .build();
                handler.handle(Future.succeededFuture(r.toJson()));
                return Future.failedFuture(COMPOSE_FAILURE);
              }
              return Future.succeededFuture();
            });

    validate
        .compose(
            i ->
                pool.withTransaction(
                    conn -> conn.preparedQuery(DELETE_DELEGATIONS).execute(queryTup)))
        .onSuccess(
            res -> {
              Response r =
                  new Response.ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_DELETE_DELE)
                      .objectResults(new JsonObject())
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            e -> {
              if (e.getMessage().equals(COMPOSE_FAILURE)) {
                return; // do nothing
              }
              LOGGER.error(e.getMessage());
              handler.handle(Future.failedFuture("Internal error"));
            });

    return this;
  }

  // TO-DO change method to accept only ResourceObject instead of resourceID
  /**
   * Create/map objects, fields to List<tuple>.
   *
   * @param request
   * @param objDetails
   * @param user
   * @return List<Tuple>
   */
  Future<List<Tuple>> mapTupleCreate(
      List<CreatePolicyNotification> request, Map<String, ResourceObj> objDetails, User user) {

    Promise<List<Tuple>> promise = Promise.promise();
    List<Tuple> tuples = new ArrayList<>();

    String userId = user.getUserId();
    try {
      for (CreatePolicyNotification each : request) {

        String catId = each.getItemId();
        ResourceObj resource = objDetails.get(catId);
        UUID itemId = resource.getId();
        UUID ownerId = resource.getOwnerId();

        String status = RoleStatus.PENDING.name();
        String itemType = each.getItemType().toUpperCase();

        Duration duration = DatatypeFactory.newInstance().newDuration(each.getExpiryDuration());
        JsonObject constraints = each.getConstraints();

        Interval interval =
            Interval.of(
                duration.getYears(),
                duration.getMonths(),
                duration.getDays(),
                duration.getHours(),
                duration.getMinutes(),
                duration.getSeconds());

        tuples.add(Tuple.of(userId, itemId, itemType, ownerId, status, interval, constraints));
      }
    } catch (DatatypeConfigurationException e) {
      LOGGER.error("Fail: {}; {}", INVALID_TUPLE, e.getLocalizedMessage());
      promise.fail(INVALID_TUPLE);
    }

    promise.complete(tuples);
    return promise.future();
  }

  /**
   * Create/map objects, fields to List<tuple>.
   *
   * @param request
   * @param request
   * @return List<Tuple>
   */
  Future<List<Tuple>> mapTupleUpdate(List<UpdatePolicyNotification> request) {

    Promise<List<Tuple>> promise = Promise.promise();
    List<Tuple> tuples = new ArrayList<>();

    try {

      for (UpdatePolicyNotification each : request) {
        String status = each.getStatus().name();
        UUID requestId = UUID.fromString(each.getRequestId());

        Duration duration = DatatypeFactory.newInstance().newDuration(each.getExpiryDuration());
        JsonObject constraints = each.getConstraints();

        Interval interval =
            Interval.of(
                duration.getYears(),
                duration.getMonths(),
                duration.getDays(),
                duration.getHours(),
                duration.getMinutes(),
                duration.getSeconds());

        tuples.add(Tuple.of(status, interval, constraints, requestId));
      }
    } catch (DatatypeConfigurationException e) {
      LOGGER.error("Fail: {}; {}", INVALID_TUPLE, e.getLocalizedMessage());
      promise.fail(INVALID_TUPLE);
    }

    promise.complete(tuples);
    return promise.future();
  }

  /**
   * Checks the duplicate access requests.
   *
   * @param tuples
   * @return
   */
  public Future<List<Tuple>> checkDuplication(List<Tuple> tuples) {
    Promise<List<Tuple>> promise = Promise.promise();

    List<Tuple> selectTuples = new ArrayList<>();
    for (int i = 0; i < tuples.size(); i++) {
      UUID userId = tuples.get(i).getUUID(0);
      UUID itemId = tuples.get(i).getUUID(1);
      UUID ownerId = tuples.get(i).getUUID(3);
      String status = RoleStatus.PENDING.name();
      selectTuples.add(Tuple.of(userId, itemId, ownerId, status));
    }
    pool.withConnection(
        conn ->
            conn.preparedQuery(SELECT_NOTIF_POLICY_REQUEST)
                .executeBatch(selectTuples)
                .onComplete(
                    dbHandler -> {
                      if (dbHandler.failed()) {
                        LOGGER.error(ERR_DUP_NOTIF_REQ + dbHandler.cause().getLocalizedMessage());
                        promise.fail(dbHandler.cause().getLocalizedMessage());

                      } else if (dbHandler.succeeded()) {
                        RowSet<Row> rows = dbHandler.result();
                        List<UUID> ids = new ArrayList<>();
                        while (rows != null) {
                          rows.iterator()
                              .forEachRemaining(
                                  row -> {
                                    ids.add(row.getUUID(ID));
                                  });
                          rows = rows.next();
                        }

                        if (ids.size() > 0) {
                          LOGGER.error("Fail: {}; Id: {}", DUP_NOTIF_REQ, ids);
                          promise.fail(DUP_NOTIF_REQ);
                        } else {
                          promise.complete(tuples);
                        }
                      }
                    }));

    return promise.future();
  }

  @Override
  public PolicyService createDelegation(
      List<CreateDelegationRequest> request,
      User user,
      JsonObject authDelegateDetails,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    boolean isAuthDelegate = !authDelegateDetails.isEmpty();
    String userId = user.getUserId();
    // check if resources and userIds in request exist in db and have roles as delegate

    if (!(isAuthDelegate || user.getRoles().contains(Roles.PROVIDER))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES)
              .status(401)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<UUID> users =
        request.stream().map(obj -> UUID.fromString(obj.getUserId())).collect(Collectors.toList());

    Future<Void> checkUserRole = createDelegate.checkUserRoles(users);

    List<String> resServers =
        request.stream().map(CreateDelegationRequest::getResSerId).collect(Collectors.toList());

    Future<Map<String, UUID>> resSerDetail = createDelegate.getResourceServerDetails(resServers);

    // check if user has policy by auth admin
    String finalUserId = userId;
    Future<Boolean> checkAuthPolicy =
        CompositeFuture.all(checkUserRole, resSerDetail)
            .compose(obj -> createDelegate.checkAuthPolicy(finalUserId));

    if (isAuthDelegate) {
      // auth delegate cannot create other auth delegates
      if (resServers.contains(authOptions.getString("authServerUrl"))) {
        Response r =
            new ResponseBuilder()
                .type(URN_INVALID_INPUT)
                .title(ERR_TITLE_AUTH_DELE_CREATE)
                .detail(ERR_TITLE_AUTH_DELE_CREATE)
                .status(403)
                .build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return this;
      }
      // if delegate then the delegation should be created by using the providers userId
      userId = authDelegateDetails.getString("providerId");
    }

    String OwnerId = userId;
    Future<List<Tuple>> item =
        checkAuthPolicy.compose(
            ar -> {
              List<Tuple> tuples = new ArrayList<>();
              for (CreateDelegationRequest createDelegationRequest : request) {
                UUID user_id = UUID.fromString(createDelegationRequest.getUserId());
                UUID resource_server_id =
                    resSerDetail.result().get(createDelegationRequest.getResSerId());
                String status = "ACTIVE";
                tuples.add(Tuple.of(OwnerId, user_id, resource_server_id, status));
              }

              return Future.succeededFuture(tuples);
            });

    Future<Boolean> insertDelegations = item.compose(createDelegate::insertItems);

    insertDelegations
        .onSuccess(
            succ -> {
              Response r =
                  new Response.ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title("added delegations")
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            obj -> {
              Response r = createDelegate.getRespObj(obj.getLocalizedMessage());
              handler.handle(Future.succeededFuture(r.toJson()));
            });
    return this;
  }
}
