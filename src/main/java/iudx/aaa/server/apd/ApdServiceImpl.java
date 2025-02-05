package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NOT_TRUSTEE;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NO_ROLES_PUT;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_CANT_CHANGE_APD_STATUS;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_DUPLICATE_REQ;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_APDID;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NOT_TRUSTEE;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NO_ROLES_PUT;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.apd.Constants.NIL_UUID;
import static iudx.aaa.server.apd.Constants.RESP_APD_ID;
import static iudx.aaa.server.apd.Constants.RESP_APD_NAME;
import static iudx.aaa.server.apd.Constants.RESP_APD_OWNER;
import static iudx.aaa.server.apd.Constants.RESP_APD_STATUS;
import static iudx.aaa.server.apd.Constants.RESP_APD_URL;
import static iudx.aaa.server.apd.Constants.RESP_OWNER_USER_ID;
import static iudx.aaa.server.apd.Constants.SQL_CHECK_ADMIN_OF_SERVER;
import static iudx.aaa.server.apd.Constants.SQL_GET_APDS_BY_ID_ADMIN;
import static iudx.aaa.server.apd.Constants.SQL_GET_APDS_BY_ID_TRUSTEE;
import static iudx.aaa.server.apd.Constants.SQL_INSERT_APD_IF_NOT_EXISTS;
import static iudx.aaa.server.apd.Constants.SQL_UPDATE_APD_STATUS;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_REGISTERED_APD;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_UPDATED_APD;
import static iudx.aaa.server.apiserver.util.Urn.*;

import com.google.common.net.InternetDomainName;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.ApdUpdateRequest;
import iudx.aaa.server.apiserver.CreateApdRequest;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The APD (Access Policy Domain) Verticle.
 * <h1>APD Verticle</h1>
 * <p>
 * The APD Verticle implementation in the the IUDX AAA Server exposes the
 * {@link iudx.aaa.server.apd.ApdService} over the Vert.x Event Bus.
 * </p>
 *
 * @version 1.0
 */
public class ApdServiceImpl implements ApdService {

  private static final Logger LOGGER = LogManager.getLogger(ApdServiceImpl.class);

  private static String AUTH_SERVER_URL;
  private PgPool pool;
  private ApdWebClient apdWebClient;
  private RegistrationService registrationService;
  private PolicyService policyService;

  public ApdServiceImpl(PgPool pool, ApdWebClient apdWebClient, RegistrationService regService,
      PolicyService polService, JsonObject options) {
    this.pool = pool;
    this.apdWebClient = apdWebClient;
    this.registrationService = regService;
    this.policyService = polService;
    AUTH_SERVER_URL = options.getString(CONFIG_AUTH_URL);
  }

  /**
   * authAdminStates and trusteeStates are Maps that determine what kind of state changes each user
   * can make. See javadoc for updateApd for allowed states. Currently, each starting state is
   * present only once, so we can have Map<ApdStatus, ApdStatus>. If this changes, we can have
   * Map<ApdStatus, Set<ApdStatus>>. Note that there is no PENDING starting state for trusteeStates.
   * Since we use '==' for equality checking of ApdStatus enum, no NPE is thrown.
   */
  static private Map<ApdStatus, ApdStatus> authAdminStates = Map.of(ApdStatus.PENDING,
      ApdStatus.ACTIVE, ApdStatus.ACTIVE, ApdStatus.INACTIVE, ApdStatus.INACTIVE, ApdStatus.ACTIVE);

  static private Map<ApdStatus, ApdStatus> trusteeStates =
      Map.of(ApdStatus.ACTIVE, ApdStatus.INACTIVE, ApdStatus.INACTIVE, ApdStatus.PENDING);

  @Override
  public ApdService listApd(User user, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ApdService updateApd(List<ApdUpdateRequest> request, User user,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      Response r = new ResponseBuilder().status(404).type(URN_MISSING_INFO)
          .title(ERR_TITLE_NO_USER_PROFILE).detail(ERR_DETAIL_NO_USER_PROFILE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    /*
     * Check for duplicate apd IDs (same apd ID, different status, OpenAPI can't catch this) by
     * checking if an apd ID is already added to the `apdIds` set
     */
    Set<UUID> apdIds = new HashSet<UUID>();
    List<UUID> requestedApdIds =
        request.stream().map(r -> UUID.fromString(r.getApdId())).collect(Collectors.toList());
    List<UUID> duplicates =
        requestedApdIds.stream().filter(id -> apdIds.add(id) == false).collect(Collectors.toList());

    if (!duplicates.isEmpty()) {
      String firstOffendingId = duplicates.get(0).toString();
      Response resp = new ResponseBuilder().type(URN_INVALID_INPUT).title(ERR_TITLE_DUPLICATE_REQ)
          .detail(firstOffendingId).status(400).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
    }

    List<Roles> roles = user.getRoles();
    Boolean isTrustee = roles.contains(Roles.TRUSTEE);
    Future<Boolean> isAuthAdmin = checkAdminServer(user);

    Future<Void> checkUserRoles = isAuthAdmin.compose(res -> {
      if (!(isTrustee || isAuthAdmin.result())) {
        return Future.failedFuture(new ComposeException(403, URN_INVALID_ROLE.toString(),
            ERR_TITLE_NO_ROLES_PUT, ERR_DETAIL_NO_ROLES_PUT));
      }
      return Future.succeededFuture();
    });

    Collector<Row, ?, Map<UUID, JsonObject>> collector =
        Collectors.toMap(row -> row.getUUID("apdId"), row -> row.toJson());

    /* In case a user has both Auth Admin and trustee roles, auth admin takes precedence */
    Future<Map<UUID, JsonObject>> queryResult = checkUserRoles.compose(n -> {
      String query;
      Tuple tuple;
      if (isAuthAdmin.result()) {
        query = SQL_GET_APDS_BY_ID_ADMIN;
        tuple = Tuple.of(apdIds.toArray(UUID[]::new));
      } else {
        query = SQL_GET_APDS_BY_ID_TRUSTEE;
        tuple = Tuple.of(apdIds.toArray(UUID[]::new), UUID.fromString(user.getUserId()));
      }
      return pool
          .withConnection(conn -> conn.preparedQuery(query).collecting(collector).execute(tuple))
          .map(res -> res.value());
    });

    Future<Void> validateStatus = queryResult.compose(map -> {
      Set<UUID> queriedIds = map.keySet();

      if (queriedIds.size() != apdIds.size()) {
        apdIds.removeAll(queriedIds);
        String firstOffender = apdIds.iterator().next().toString();
        return Future.failedFuture(new ComposeException(400, URN_INVALID_INPUT.toString(),
            ERR_TITLE_INVALID_APDID, firstOffender));
      }

      Map<UUID, ApdStatus> currentStatus = map.entrySet().stream().collect(Collectors
          .toMap(i -> i.getKey(), i -> ApdStatus.valueOf(i.getValue().getString("status"))));

      Map<UUID, ApdStatus> desiredStatus = request.stream()
          .collect(Collectors.toMap(i -> UUID.fromString(i.getApdId()), i -> i.getStatus()));

      if (isAuthAdmin.result()) {
        return checkValidStatusChange(authAdminStates, currentStatus, desiredStatus);
      } else {
        return checkValidStatusChange(trusteeStates, currentStatus, desiredStatus);
      }
    });

    /*
     * Function to get list of trustee user IDs who's APDs are being set to ACTIVE state. Auth Admin
     * policies will be set for these trustees (whether they already have them or not). If not an
     * auth admin, send empty list to skip the policy set.
     */
    Supplier<List<UUID>> trusteesWithActiveApds = () -> {
      if (!isAuthAdmin.result()) {
        return new ArrayList<UUID>();
      }

      List<UUID> ids = request.stream().filter(r -> r.getStatus() == ApdStatus.ACTIVE)
          .map(r -> UUID.fromString(r.getApdId())).collect(Collectors.toList());

      return ids.stream().map(r -> queryResult.result().get(r).getString("owner_id"))
          .map(id -> UUID.fromString(id)).collect(Collectors.toList());
    };

    /* Function to get list of trustee user IDs from the query result map */
    Supplier<List<String>> trusteeIds = () -> {
      return queryResult.result().entrySet().stream()
          .map(obj -> obj.getValue().getString("owner_id")).collect(Collectors.toList());
    };

    validateStatus.compose(success -> {
      List<Tuple> tuple =
          request.stream().map(req -> Tuple.of(req.getStatus(), UUID.fromString(req.getApdId())))
              .collect(Collectors.toList());

      return pool
          .withTransaction(conn -> conn.preparedQuery(SQL_UPDATE_APD_STATUS).executeBatch(tuple)
              .compose(succ -> setAuthAdminPolicy(user, trusteesWithActiveApds.get()))
              .compose(x -> getTrusteeDetails(trusteeIds.get())));

    }).onSuccess(trusteeDetails -> {
      JsonArray response = new JsonArray();
      Map<UUID, JsonObject> apdDetails = queryResult.result();

      for (ApdUpdateRequest req : request) {
        UUID apdId = UUID.fromString(req.getApdId());
        JsonObject obj = apdDetails.get(apdId);

        obj.remove(RESP_APD_STATUS);
        obj.put(RESP_APD_STATUS, req.getStatus().toString().toLowerCase());

        String ownerId = (String) obj.remove("owner_id");
        obj.put(RESP_APD_OWNER, trusteeDetails.get(ownerId).put(RESP_OWNER_USER_ID, ownerId));

        response.add(obj);
        LOGGER.info("APD status updated : " + apdId.toString());
      }

      Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS)
          .title(SUCC_TITLE_UPDATED_APD).arrayResults(response).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
    }).onFailure(e -> {
      if (e instanceof ComposeException) {
        ComposeException exp = (ComposeException) e;
        handler.handle(Future.succeededFuture(exp.getResponse().toJson()));
        return;
      }
      LOGGER.error(e.getMessage());
      handler.handle(Future.failedFuture("Internal error"));
    });

    return this;
  }

  /**
   * Check if list of APDs in some state A can be changed to a state B based on privileges.
   * 
   * @param allowedStates a Map of ApdStatus to ApdStatus that determines allowed state changes
   * @param currentStatus a Map of APD IDs (UUIDs) to the current state they are in (ApdStatus)
   * @param desiredStatus a Map of APD IDs (UUIDs) to the state they require to be changed to
   *        (ApdStatus)
   * @return a void Future. If a state change is not allowed, a ComposeException is thrown with the
   *         APD ID in the response detail
   */
  private Future<Void> checkValidStatusChange(Map<ApdStatus, ApdStatus> allowedStates,
      Map<UUID, ApdStatus> currentStatus, Map<UUID, ApdStatus> desiredStatus) {
    Promise<Void> p = Promise.promise();

    for (Entry<UUID, ApdStatus> entry : currentStatus.entrySet()) {

      ApdStatus current = entry.getValue();
      ApdStatus desired = desiredStatus.get(entry.getKey());

      /* If allowedStates.get(x) is null, == does not throw an NullPointerException */
      Boolean validStatusChange = allowedStates.get(current) == desired;

      if (!validStatusChange) {
        String firstOffender = entry.getKey().toString();
        p.fail(new ComposeException(403, URN_INVALID_INPUT.toString(),
            ERR_TITLE_CANT_CHANGE_APD_STATUS, firstOffender));
        return p.future();
      }
    }
    p.complete();
    return p.future();
  }

  /**
   * Set auth admin policies for trustees whose APDs are going to ACTIVE state. As the trustee may
   * already have the policy, the function handles both 'Created Policy' and 'Already Exists'. Due
   * to this, the createPolicy method is called with individual requests instead of a list of
   * requests ('Already Exists' will not allow the rest of the policies to be set if sent in list).
   * 
   * @param user The User object, in this case the Auth Admin
   * @param activeTrustees list of user IDs of trustees in UUID
   * @return a void future. If a policy is not set (for a reason other than already exists) or the
   *         policy service fails, a failed future is returned.
   */
  private Future<Void> setAuthAdminPolicy(User user, List<UUID> activeTrustees) {

    Promise<Void> response = Promise.promise();
    /* Exit early if no trustee APDs going to active state or not auth admin */
    if (activeTrustees.size() == 0) {
      response.complete();
      return response.future();
    }

    @SuppressWarnings("rawtypes")
    List<Future> futures = new ArrayList<>();

    for (UUID id : activeTrustees) {
      JsonObject obj = new JsonObject();
      obj.put("userId", id.toString());
      obj.put("itemId", AUTH_SERVER_URL);
      obj.put("constraints", new JsonObject());
      obj.put("itemType", "resource_server");
      CreatePolicyRequest req = new CreatePolicyRequest(obj);
      Promise<JsonObject> promise = Promise.promise();
      policyService.createPolicy(List.of(req), user, new JsonObject(), promise);
      futures.add(promise.future());
    }

    CompositeFuture.all(futures).onSuccess(res -> {
      List<JsonObject> result = res.list();
      Boolean success =
          result.stream().allMatch(obj -> obj.getString("type").equals(URN_SUCCESS.toString())
              || obj.getString("type").equals(URN_ALREADY_EXISTS.toString()));
      if (success) {
        response.complete();
      } else {
        response.fail("Failed to set admin policy");
      }
    }).onFailure(res -> {
      response.fail("Failed to set admin policy");
    });
    return response.future();
  }

  @Override
  public ApdService createApd(CreateApdRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (!user.getRoles().contains(Roles.TRUSTEE)) {
      Response resp = new ResponseBuilder().type(URN_INVALID_ROLE).title(ERR_TITLE_NOT_TRUSTEE)
          .detail(ERR_DETAIL_NOT_TRUSTEE).status(403).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
    }

    String url = request.getUrl().toLowerCase();
    String name = request.getName();
    UUID trusteeId = UUID.fromString(user.getUserId());

    if (!InternetDomainName.isValid(url)) {
      Response resp = new ResponseBuilder().type(URN_INVALID_INPUT).title(ERR_TITLE_INVALID_DOMAIN)
          .detail(ERR_DETAIL_INVALID_DOMAIN).status(400).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
    }

    Tuple tuple = Tuple.of(name, url, trusteeId);
    Future<Boolean> isApdOnline = apdWebClient.checkApdExists(url);

    Future<UUID> apdId = isApdOnline
        .compose(success -> pool.withTransaction(
            conn -> conn.preparedQuery(SQL_INSERT_APD_IF_NOT_EXISTS).execute(tuple)))
        .compose(res -> {
          if (res.size() == 0) {
            return Future.failedFuture(new ComposeException(409, URN_ALREADY_EXISTS.toString(),
                ERR_TITLE_EXISTING_DOMAIN, ERR_DETAIL_EXISTING_DOMAIN));
          }
          return Future.succeededFuture(res.iterator().next().getUUID(0));
        });

    Future<Map<String, JsonObject>> trusteeDetailsFut =
        apdId.compose(success -> getTrusteeDetails(List.of(trusteeId.toString())));

    trusteeDetailsFut.onSuccess(trusteeDetails -> {
      JsonObject response = new JsonObject();
      response.put(RESP_APD_ID, apdId.result().toString()).put(RESP_APD_NAME, name)
          .put(RESP_APD_URL, url).put(RESP_APD_STATUS, ApdStatus.PENDING.toString().toLowerCase());

      JsonObject ownerDetails = trusteeDetails.get(trusteeId.toString());
      ownerDetails.put(RESP_OWNER_USER_ID, trusteeId.toString());
      response.put(RESP_APD_OWNER, ownerDetails);

      LOGGER.info("APD registered with id : " + apdId.result().toString());

      Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS)
          .title(SUCC_TITLE_REGISTERED_APD).objectResults(response).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
    }).onFailure(e -> {
      if (e instanceof ComposeException) {
        ComposeException exp = (ComposeException) e;
        handler.handle(Future.succeededFuture(exp.getResponse().toJson()));
        return;
      }
      LOGGER.error(e.getMessage());
      handler.handle(Future.failedFuture("Internal error"));
    });

    return this;
  }

  @Override
  public ApdService getApdDetails(List<String> apdIds, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub

    return null;
  }

  /**
   * Calls RegistrationService.getUserDetails to specifically get details of trustees.
   * 
   * @param userIds List of strings of user IDs
   * @return a future of a Map, mapping the string user ID to a JSON object containing the user
   *         details
   */
  private Future<Map<String, JsonObject>> getTrusteeDetails(List<String> userIds) {
    Promise<Map<String, JsonObject>> promise = Promise.promise();
    Promise<JsonObject> regServicePromise = Promise.promise();
    Future<JsonObject> response = regServicePromise.future();

    registrationService.getUserDetails(userIds, regServicePromise);
    response.onSuccess(obj -> {
      Map<String, JsonObject> details = obj.stream().collect(
          Collectors.toMap(val -> (String) val.getKey(), val -> (JsonObject) val.getValue()));
      promise.complete(details);
    }).onFailure(err -> {
      LOGGER.error(err.getMessage());
      promise.fail("Get user details failed");
    });

    return promise.future();
  }


  /**
   * Check if a user is an admin for a particular server.
   * 
   * @param user The user object
   * @param serverUrl The URL/domain of the particuar server
   * @return Future of Boolean type
   */
  private Future<Boolean> checkAdminServer(User user) {
    Promise<Boolean> p = Promise.promise();

    if (!user.getRoles().contains(Roles.ADMIN)) {
      p.complete(false);
      return p.future();
    }

    pool.withConnection(conn -> conn.preparedQuery(SQL_CHECK_ADMIN_OF_SERVER)
        .execute(Tuple.of(user.getUserId(), AUTH_SERVER_URL)).map(row -> row.size()))
        .onSuccess(size -> p.complete(size == 0 ? false : true))
        .onFailure(error -> p.fail(error.getMessage()));

    return p.future();
  }
}
